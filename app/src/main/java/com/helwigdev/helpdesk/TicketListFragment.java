package com.helwigdev.helpdesk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.crash.FirebaseCrash;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by helwig on 10/14/2015.
 */

public class TicketListFragment extends ListFragment implements RNInterface {

    private static final String TAG = "TicketListFragment";

    private ArrayList<Ticket> tickets;
    private SharedPreferences preferences;


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Ticket t = ((TicketAdapter) getListAdapter()).getItem(position);
        //mCallbacks.onTicketSelected(t);
        Intent i = new Intent(getContext(), TicketViewActivity.class);
        i.putExtra(Ticket.KEY_TICKET_ID, t.getTicketId());
        i.putExtra(Ticket.KEY_TICKET_PRETTY_UPDATED, t.getPrettyLastUpdated());

        startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.app_name);
        //get list of tickets
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        initGetTickets();
        setRetainInstance(false);

    }

    private void initGetTickets(){
        String sUrl = "http://" +
                preferences.getString(Init.PREF_SERVER, "") +
                "/helpdesk/WebObjects/Helpdesk.woa/ra/Tickets/mine" +
                "?page=1&limit=500" +
                "&sessionKey=" +
                preferences.getString(Init.PREF_SESSION_KEY, "");

        try {
            String cookie = preferences.getString(Init.PREF_COOKIE, "");
            new ReadNetwork(0, this, true, cookie).execute(new URL(sUrl));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    public void refresh(){
        Log.d(TAG, "Refreshing tickets");
        TicketSingleton.getInstance().clear();
        initGetTickets();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setDivider(null);
    }

    private void dismissActivityPd(){
        try{
            TabActivity a = (TabActivity) getActivity();
            a.myTicketsRefreshed = true;
            a.dismissPd();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void processResult(String output, int taskID) {
        dismissActivityPd();
        switch (taskID) {
            case 0:
                //process initial result
                Log.d(TAG, output);
                try {
                    JSONArray array = new JSONArray(output);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        TicketSingleton.getInstance().addTicket(new Ticket(o, Ticket.TYPE_SHORT));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    FirebaseCrash.report(new Exception("JSONException: TicketListFragment: " + e.toString()));
                }
                TicketAdapter adapter = new TicketAdapter(TicketSingleton.getInstance().getTickets());
                setListAdapter(adapter);
                break;
            default:

        }
    }

    @Override
    public void authErr(int type, int taskId) {
        if (this.isDetached()) return;
        dismissActivityPd();
        switch (type) {
            case 401:
                //deauth
                Toast.makeText(getActivity(), "Session expired - please exit and re-open app", Toast.LENGTH_SHORT).show();
                break;
            case 403:
                //not allowed
                Toast.makeText(getActivity(), "You are not allowed to do that.", Toast.LENGTH_SHORT).show();
                break;
            case 404:
                //not allowed
                Toast.makeText(getActivity(), "404 Not Found. Is the URL correct?", Toast.LENGTH_SHORT).show();
                break;
            case 444:
                Toast.makeText(getActivity(),"Network timeout",Toast.LENGTH_LONG).show();
                break;
            case 503:
                Toast.makeText(getActivity(),"503 service unavailable. Try again in a few minutes",Toast.LENGTH_LONG).show();
                break;
            default:
                Toast.makeText(getActivity(), "Server returned error: " + type, Toast.LENGTH_SHORT).show();
                FirebaseCrash.report(new Exception("TLF: Server returned error: Task: " + taskId + " Type: " + type));
        }
    }

    @Override
    public void setCookie(String cookie) {
        //do nothing - not used here
    }

    public interface Callbacks {
    }

    private class TicketAdapter extends ArrayAdapter<Ticket> {

        public TicketAdapter(ArrayList<Ticket> tickets) {
            super(getActivity(), 0, tickets);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater()
                        .inflate(R.layout.part_ticket, null);
            }

            Ticket t = getItem(position);

            TextView tvId = (TextView) convertView.findViewById(R.id.tv_tick_id);
            TextView tvClient = (TextView) convertView.findViewById(R.id.tv_tick_client);
            TextView tvUpdated = (TextView) convertView.findViewById(R.id.tv_tick_last_updated);
            TextView tvSubj = (TextView) convertView.findViewById(R.id.tv_tick_subj);
            TextView tvDetail = (TextView) convertView.findViewById(R.id.tv_tick_det);

            String tId = String.valueOf(t.getTicketId());
            tvId.setText(tId);
            tvClient.setText(t.getDisplayClient());
            tvUpdated.setText(t.getPrettyLastUpdated());
            tvSubj.setText(t.getShortSubject());
            tvDetail.setText(Html.fromHtml(t.getShortDetail()));//detail may include html markup


            return convertView;
        }
    }

}

package io.radio.android;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by aki on 1/07/13.
 */
public class RequestActivity extends ListActivity {

    private SongAdapter adapter = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.request_layout);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            new SearchTask().execute(query);
        }
    }

    protected class SearchPage {
        public boolean status;
        public String cooldown;
        public RequestSong[] results;
        public int pages;
        public int page;
        public boolean hasResults;

        public SearchPage(String json) {
            try {
                System.out.println(json);
                JSONObject obj = new JSONObject(json);
                pages = obj.getInt("pages");

                if (pages > 0) {
                    status = obj.getBoolean("status");
                    cooldown = obj.getString("cooldown");
                    page = obj.getInt("page");

                    JSONArray songArray = obj.getJSONArray("result");
                    ArrayList<RequestSong> requestSongs = new ArrayList<RequestSong>();
                    for (int i = 0; i < songArray.length(); i++) {
                        JSONArray songObj = (JSONArray) songArray.get(i);
                        RequestSong song = new RequestSong(songObj);
                        if (status == false) { // if AFK streamer is on
                            song.setRequestable(false);
                        }
                        requestSongs.add(new RequestSong(songObj));
                    }

                    Object[] array = requestSongs.toArray();
                    results =  Arrays.copyOf(array, array.length, RequestSong[].class);

                    hasResults = true;
                } else {
                    hasResults = false;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    };

    protected class RequestSong {
        public String artistName;
        public String songName;
        public long lastPlayed;
        public long lastRequested;
        public int songId;
        public boolean isRequestable;

        public RequestSong(JSONArray array) {
            try {
                artistName = array.getString(0);
                songName = array.getString(1);
                lastPlayed = array.getLong(2);
                lastRequested = array.getLong(3);
                songId = array.getInt(4);
                isRequestable = array.getBoolean(5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setRequestable(boolean flag) {
            isRequestable = flag;
        }
    };

    protected class SongAdapter extends ArrayAdapter<RequestSong> {
        private ArrayList<RequestSong> songs;
        private Context c;
        public SongAdapter(Context context, int textViewResourceId, ArrayList<RequestSong> songs) {
            super(context, textViewResourceId, songs);
            this.songs = songs;
            this.c = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.request_row, null);
            }
            RequestSong song = songs.get(position);
            TextView songName = (TextView) v.findViewById(R.id.songName);
            TextView artistName = (TextView) v.findViewById(R.id.artistName);
            TextView songId = (TextView) v.findViewById(R.id.songId);
            TextView lastPlayed = (TextView) v.findViewById(R.id.lastPlayed);
            TextView lastRequested = (TextView) v.findViewById(R.id.lastRequested);
            Button requestButton = (Button) v.findViewById(R.id.requestButton);

            songName.setText(song.songName);
            artistName.setText(song.artistName);

            Date lastPlayedDate = new Date();
            lastPlayedDate.setTime(song.lastPlayed * 1000);
            Date lastRequestedDate = new Date();
            lastRequestedDate.setTime(song.lastRequested * 1000);

            String lastPlayedString;
            String lastRequestedString;
            DateFormat dateFormat = new DateFormat();
            lastPlayedString = dateFormat.format("E d MMM, k:mm", lastPlayedDate).toString();
            lastRequestedString = dateFormat.format("E d MMM, k:mm", lastRequestedDate).toString();

            lastPlayed.setText(lastPlayedString);
            lastRequested.setText(lastRequestedString);

            requestButton.setHint(Integer.toString(song.songId));

            if (song.isRequestable) {
                requestButton.setEnabled(false);
            } else {
                requestButton.setEnabled(true);
            }

            requestButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Button button = (Button) v;
                    new PostRequestTask().execute(button.getHint().toString());
                    // post to server
                }
            });

            return v;
        }
    }

    private class PostRequestTask extends AsyncTask<String, Void, Void> {
        public String postBody;

        protected Void doInBackground(String... songId) {
            try {
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost("http://r-a-d.io/request/");
                List<NameValuePair> params = new ArrayList<NameValuePair>(1);
                params.add(new BasicNameValuePair("songid", songId[0]));
                httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
                HttpResponse response = httpClient.execute(httpPost);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream in = entity.getContent();
                    try {
                        BufferedReader buf = new BufferedReader(new InputStreamReader(in));
                        StringBuilder sb = new StringBuilder();
                        String str;
                        while ((str = buf.readLine()) != null) {
                            sb.append(str);
                        }

                        postBody = sb.toString();

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        in.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(Void v) {
            // check if song requested
            System.out.println(postBody);
            Toast.makeText(getApplicationContext(), "Song successfully requested!", Toast.LENGTH_LONG).show();
        }
    }

    private class SearchTask extends AsyncTask<String, Void, Void> {

        ArrayList<SearchPage> searchPages;

        protected Void doInBackground(String... query) {
            searchPages = new ArrayList<SearchPage>();
            try {
                SearchPage searchPage = new SearchPage(readJSON(query[0], 1));
                searchPages.add(searchPage);
                if (searchPage.hasResults)
                if (searchPage.pages > 1) {
                    for (int i = 2; i <= searchPage.pages; i++) {
                        searchPages.add(new SearchPage(readJSON(query[0], i)));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        protected String readJSON(String query, int page) {
            String result = "";
            try {
                String urlEncodedQuery = URLEncoder.encode(query, "UTF-8");
                URL apiURl = new URL(getString(R.string.searchApiURL) + "?query=" + urlEncodedQuery + "&page=" + Integer.toString(page));
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        apiURl.openStream()));
                result = in.readLine();
                in.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        protected void onPostExecute(Void v) {
            ArrayList<RequestSong> songs = new ArrayList<RequestSong>();
            for (SearchPage page : searchPages) {
                if (page.hasResults)
                    for (RequestSong song : page.results) {
                        songs.add(song);
                    }
            }
            adapter = new SongAdapter(getApplicationContext(), R.layout.request_row, songs);
            setListAdapter(adapter);

        }
    }
}
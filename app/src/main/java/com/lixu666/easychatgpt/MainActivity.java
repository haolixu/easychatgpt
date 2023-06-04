package com.lixu666.easychatgpt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lixu666.easychatgpt.R;
import com.lixu666.tokenizers.Constants;
import com.lixu666.tokenizers.GPT2Tokenizer;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.sigmob.windad.WindAdOptions;
import com.sigmob.windad.WindAds;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    private AdView adView;
    ImageButton sendButton;
    List<Message> messageList;
    MessageAdapter messageAdapter;
    private final String TAG = "ChatGPT";
    /*请在config.xml中配置openai apiKey值
    https://openai.com
    */
    private String apiKey;
    private Session mySession;
    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)       //设置连接超时
            .readTimeout(60, TimeUnit.SECONDS)          //设置读超时
            .writeTimeout(60, TimeUnit.SECONDS)          //设置写超时
            .build();                                   //构建OkHttpClient对象
    private final View.OnLongClickListener lc = v -> {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", ((TextView) v).getText());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getApplicationContext(), "复制成功", Toast.LENGTH_SHORT).show();
        return false;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //谷歌广告
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {}
        });
        MobileAds.setRequestConfiguration(
                new RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABCDEF012345"))
                        .build());
        // Gets the ad view defined in layout/ad_fragment.xml with ad unit ID set in
        // values/strings.xml.
        adView = findViewById(R.id.ad_view);
        // Create an ad request.
        AdRequest adRequest = new AdRequest.Builder().build();
        // Start loading the ad in the background.
        adView.loadAd(adRequest);
        //谷歌广告end


        messageList = new ArrayList<>();
        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);
        //setup Session
        apiKey = getString(R.string.apiKey);
        String character_desc = getString(R.string.character_desc);
        int conversation_max_tokens = Integer.parseInt(getString(R.string.conversation_max_tokens));
        mySession = new Session(tokenizerFromPretrained(), conversation_max_tokens, character_desc);

        //setup recycler view
        messageAdapter = new MessageAdapter(messageList, lc);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener((v) -> {

            try {
                String question = messageEditText.getText().toString().trim();
                addToChat(question, Message.SENT_BY_ME);
                messageEditText.setText("");
                callAPI(question);
                welcomeTextView.setVisibility(View.GONE);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private GPT2Tokenizer tokenizerFromPretrained() {
        InputStream encoderInputStream;
        InputStream bpeInputStream;
        try {
            AssetManager assetManager = getAssets();
            String path = "tokenizers/gpt2";
            encoderInputStream = assetManager.open(path + "/" + Constants.ENCODER_FILE_NAME);
            bpeInputStream = assetManager.open(path + "/" + Constants.VOCAB_FILE_NAME);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return GPT2Tokenizer.fromPretrained(encoderInputStream, bpeInputStream);
    }

    void addToChat(String message, String sentBy) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageList.add(new Message(message, sentBy));
                messageAdapter.notifyDataSetChanged();
                recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            }
        });
    }

    void addResponse(String response) {
        messageList.remove(messageList.size() - 1);
        addToChat(response, Message.SENT_BY_BOT);
    }

    void callAPI(String question) throws JSONException {
        if (question.compareToIgnoreCase("#清除记忆") == 0) {
            mySession.clearSession();
            addToChat("记忆已清除", Message.SENT_BY_BOT);
            return;
        }
        //okhttp
        messageList.add(new Message(getString(R.string.Typing), Message.SENT_BY_BOT));
        JSONArray newQuestion = mySession.buildSessionQuery(question);

        JSONObject jsonBody = new JSONObject();
        try {
            //String newQuery = newQuestion.toString();
            jsonBody.put("model", "gpt-3.5-turbo");//text-davinci-003
            jsonBody.put("messages", newQuestion);
            jsonBody.put("max_tokens", 1200);// 回复最大的字符数
            jsonBody.put("temperature", 0.9);//值在[0,1]之间，越大表示回复越具有不确定性
            jsonBody.put("top_p", 1);
            jsonBody.put("frequency_penalty", 0.0);
            jsonBody.put("presence_penalty", 0.0);
            //           jsonBody.put("stop", "\n\n\n");

        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url("https://a.onepice.asia:8089/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse(getString(R.string.failed_load_response) + e.getMessage());
//                mySession.clearSession();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");
//                        Log.i(TAG, jsonArray.toString());
                        JSONObject jsonTokens = jsonObject.getJSONObject("usage");
                        int total_tokens = jsonTokens.getInt("total_tokens");
                        int completion_tokens = jsonTokens.getInt("completion_tokens");
                        if (completion_tokens > 0) {
                            String result = jsonArray.getJSONObject(0).getJSONObject("message").getString("content");//text
                            addResponse(result.trim());
                            mySession.saveSession(total_tokens, result);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    addResponse(getString(R.string.failed_load_response) + response.body().toString());
//                    mySession.clearSession();
                }
            }
        });


    }


}





















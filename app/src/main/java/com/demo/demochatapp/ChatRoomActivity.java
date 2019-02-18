package com.demo.demochatapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.demo.demochatapp.adapters.ChatRoomRecyclerAdapter;
import com.demo.demochatapp.dbHelper.ChatMasterUpdateUtility;
import com.demo.demochatapp.dbHelper.DBHelper;
import com.demo.demochatapp.models.ChatItemModel;
import com.demo.demochatapp.utilities.Hashdefine;
import com.demo.demochatapp.utilities.SharedPrefs;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatRoomActivity extends AppCompatActivity {

    RecyclerView chatHistoryRecyclerView;
    EditText messageContent;
    ImageView sendBtn;

    SharedPrefs sharedPrefs;

    MqttAndroidClient mqttAndroidClient;


    static ChatRoomRecyclerAdapter chatRoomRecyclerAdapter;
    static List<ChatItemModel> chatItemModelList = new ArrayList<>();

    DBHelper dbHelper;
    SQLiteDatabase db;
    ChatMasterUpdateUtility chatMasterUpdateUtility;

    //for getting all the data from db first; later on it is disabled
    //created in case application is resumed
    static boolean isDataLoadedFirstTime = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        chatHistoryRecyclerView = (RecyclerView) findViewById(R.id.chatHistoryRecyclerView);
        messageContent = (EditText) findViewById(R.id.sendMessageEditText);
        sendBtn = (ImageView) findViewById(R.id.sendMessageBtn);


        sharedPrefs = new SharedPrefs(getApplicationContext());
        dbHelper = new DBHelper(getApplicationContext());
        db = dbHelper.getWritableDatabase();
        chatMasterUpdateUtility = new ChatMasterUpdateUtility(getApplicationContext());


        if (!isDataLoadedFirstTime) {
            Cursor cursor = db.rawQuery("SELECT * FROM " + ChatItemModel.TABLE_NAME, null);
            cursor.moveToFirst();

            for(int i = 0;i<cursor.getCount(); i++) {
                ChatItemModel current = new ChatItemModel();
                current.setMessageID(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_ID)));
                current.setContentType(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_CONTENT_TYPE)));
                current.setMessage(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_CONTENT)));
                current.setSender(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_SENDER_ID)));
                current.setSentDateTime(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_SENT_DATETIME)));
                current.setMessageSentStatusSuccess(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_IS_MESSAGE_SENT_SUCCESSFULLY)) == 1);

//                int isPublished = cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_IS_MESSAGE_PUBLISHED_SUCCESSFULLY));
//                Log.e("VAL isPublished", String.valueOf(isPublished));
//                if (isPublished == 0) {
//                    publishPendingMessage(current);
//                }

                chatItemModelList.add(current);
                cursor.moveToNext();
            }

            Collections.reverse(chatItemModelList);
            isDataLoadedFirstTime = true;
            cursor.close();
        }

        chatRoomRecyclerAdapter = new ChatRoomRecyclerAdapter(getApplicationContext(), chatItemModelList);
        chatHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.VERTICAL, true));
        chatHistoryRecyclerView.setAdapter(chatRoomRecyclerAdapter);


        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), Hashdefine.CHAT_SERVER, sharedPrefs.getUsername());

        try{
            mqttAndroidClient.connect(Hashdefine.mqttConnectOptions(getApplicationContext()), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.e("mqtt", "connected");
                    //initializing buffers for mqtt
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                    retryPendingPublishes();
//                    ChatItemModel current = new ChatItemModel();
//                    current.setMessage(getString(R.string.label_connected_to_topic, Hashdefine.USER_SUBSCRIPTION_TOPIC));
//                    current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
//                    current.setSender(sharedPrefs.getUsername());
//                    current.setContentType("alert");
//                    chatItemModelList.add(0,current);
//
//                    chatRoomRecyclerAdapter.notifyItemInserted(chatItemModelList.size() - 1);
//                    Log.e("mqtt buffer", "initialized");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e("mqtt", "connection failed -- " + exception.toString());

                    ChatItemModel current = new ChatItemModel();
                    current.setMessage(getString(R.string.label_connection_to_topic_failed, Hashdefine.USER_SUBSCRIPTION_TOPIC));
                    current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
                    current.setSender(sharedPrefs.getUsername());
                    current.setContentType("alert");
                    chatItemModelList.add(0,current);

                    chatRoomRecyclerAdapter.notifyDataSetChanged();
                }


            });

        } catch (MqttException e) {
            e.printStackTrace();
            ChatItemModel current = new ChatItemModel();
            current.setMessage(getString(R.string.label_connection_to_topic_failed, Hashdefine.USER_SUBSCRIPTION_TOPIC));
            current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
            current.setSender(sharedPrefs.getUsername());
            current.setContentType("alert");
            chatItemModelList.add(0,current);

            chatRoomRecyclerAdapter.notifyDataSetChanged();
        }

        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                sendTopicJoinedBroadCast(reconnect);
            }

            @Override
            public void connectionLost(Throwable cause) {
                showConnectionLost();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.e("MESSAGE", "RECEIVED -- " + new String(message.getPayload()));

                String messageContents = new String(message.getPayload());

                if (!messageContents.equals("User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is offline")
                        && !messageContents.equalsIgnoreCase("User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is offline")) {
                    JSONObject jsonObject = new JSONObject(messageContents);
                    int messageID = jsonObject.getInt("message_id");
                    String sender = jsonObject.getString("sender");
                    String msg = jsonObject.getString("message");
                    String dateTime = jsonObject.getString("datetime");
                    String contentType = jsonObject.getString("content_type");

                    //preventing multiple messages with same message id
                    Cursor cursor = db.rawQuery("SELECT * FROM " + ChatItemModel.TABLE_NAME + " WHERE " + ChatItemModel.KEY_MESSAGE_ID + "=" + messageID , null);
                    cursor.moveToFirst();

                    if (chatMasterUpdateUtility.updateStatus(messageID, true) > 0) {
//                        Log.e("VAL", "UPDATED --- true");
                    }else {
//                        Log.e("VAL", "UPDATE --- failed");
                    }
                    chatRoomRecyclerAdapter.notifyDataSetChanged();
//                    Log.e("ADAPTER", "NOTIFIED");

                    if (cursor.getCount() > 0) {
                        return;
                    }

                    cursor.close();


                    if (sharedPrefs.getUsername().equals(sender)) {
                        return;
                    }

                    ChatItemModel current = new ChatItemModel();
                    current.setMessage(msg);
                    current.setSentDateTime(Hashdefine.getReceivedDateTimeInCurrentLocale(dateTime));
                    current.setSender(sender);
                    current.setMessageID(messageID);
                    current.setContentType(contentType);
                    current.setMessageSentStatusSuccess(false);


                    if (chatMasterUpdateUtility.insert(current) > 0 && contentType.equalsIgnoreCase("message")) {
//                        Log.e("item insertion", "success");
                    }else {
//                        Log.e("item insertion", "fail");
                    }


                    chatItemModelList.add(0, current);
//                    Log.e("VALUE ", "ADDED");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            chatRoomRecyclerAdapter.notifyDataSetChanged();
//                            Log.e("VALUE ", "NOTIFIED ");
                        }
                    });

                }else {
                    // for wills
                    if (!messageContents.equalsIgnoreCase("User " + "\'" + sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is offline")) {

                        String sender = "N/A";
                        String dateTime = "N/A";
                        String contentType = "N/A";

                        ChatItemModel current = new ChatItemModel();
                        current.setMessage(messageContents);
                        current.setSentDateTime(dateTime);
                        current.setSender(sender);
                        current.setContentType(contentType);

                        chatItemModelList.add(0, current);

                        chatRoomRecyclerAdapter.notifyDataSetChanged();
                    }

                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                try {
                    chatMasterUpdateUtility.updateStatus(token.getMessage().getId(), true);
                    chatRoomRecyclerAdapter.notifyDataSetChanged();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });


        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (messageContent.getText().toString().isEmpty()) {
                    messageContent.setError(getString(R.string.label_empty_text_field_required));
                    messageContent.requestFocus();
                    return;
                }

                //publish message
                publishUserMessage(messageContent.getText().toString(), "message");

                messageContent.setText("");
                messageContent.setHint(getString(R.string.label_write_something_here));
            }
        });
    }

    private void retryPendingPublishes() {
        Cursor cursor = db.rawQuery("SELECT * FROM " + ChatItemModel.TABLE_NAME, null);
        cursor.moveToFirst();

        for(int i = 0;i<cursor.getCount(); i++) {
            ChatItemModel current = new ChatItemModel();
            current.setMessageID(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_ID)));
            current.setContentType(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_CONTENT_TYPE)));
            current.setMessage(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_CONTENT)));
            current.setSender(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_SENDER_ID)));
            current.setSentDateTime(cursor.getString(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_SENT_DATETIME)));
            current.setMessageSentStatusSuccess(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_IS_MESSAGE_SENT_SUCCESSFULLY)) == 1);

            int isPublished = cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_IS_MESSAGE_PUBLISHED_SUCCESSFULLY));
//            Log.e("VAL isPublished", String.valueOf(isPublished));
            if (isPublished == 0) {
                publishPendingMessage(current);
            }

            cursor.moveToNext();
        }

        cursor.close();
    }


    private void showConnectionLost() {
        //show 'connection lost' to user

        ChatItemModel current = new ChatItemModel();
        current.setMessage(getString(R.string.label_connection_lost));
        current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
        current.setSender(sharedPrefs.getUsername());
        current.setContentType("alert");
        chatItemModelList.add(0,current);

        chatRoomRecyclerAdapter.notifyDataSetChanged();

        //mqtt will take care of sending broadcast (will) to other users
        //mqtt doesnt send will message :'(
        //publishUserMessage(sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + " disconnected", "alert");
    }

    private void sendTopicJoinedBroadCast(boolean reconnect) {
        if (reconnect) {
            //show to user that '<topic> joined'
            ChatItemModel current = new ChatItemModel();
            current.setMessage(getString(R.string.label_reconnected_to_topic, Hashdefine.USER_SUBSCRIPTION_TOPIC));
            current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
            current.setSender(sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")));
            current.setContentType("alert");
            chatItemModelList.add(0,current);
            chatRoomRecyclerAdapter.notifyDataSetChanged();
        }else {
            //show to user that '<topic> joined'
            ChatItemModel current = new ChatItemModel();
            current.setMessage(getString(R.string.label_connected_to_topic, Hashdefine.USER_SUBSCRIPTION_TOPIC));
            current.setSentDateTime(Hashdefine.getCurrentDateTimeInUTC());
            current.setSender(sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")));
            current.setContentType("alert");
            chatItemModelList.add(0,current);
            chatRoomRecyclerAdapter.notifyDataSetChanged();
        }

        retryPendingPublishes();

//        if (sharedPrefs.getIsNewJoineeBroadCasted()) {
//            //send broadcast to other users
//            publishUserMessage( "User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " has joined the chat", "alert");
//            sharedPrefs.setIsNewJoineeBroadcasted(true);
//        }/*else {
            //send broadcast to other users
//            publishUserMessage( "User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is online", "alert");
//        }*/

    }

    private void publishPendingMessage(ChatItemModel current) {
        MqttMessage message = new MqttMessage();

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("message_id", current.getMessageID());
            jsonObject.put("sender", sharedPrefs.getUsername());
            jsonObject.put("message", current.getMessage());
            jsonObject.put("datetime", Hashdefine.getCurrentDateTimeInUTC());
            jsonObject.put("content_type", current.getContentType());

            message.setId(current.getMessageID());
            message.setQos(0);
            message.setPayload(jsonObject.toString().getBytes());

            mqttAndroidClient.publish(Hashdefine.CHAT_PUBLISH_TOPIC, message);

            try {
                if (!mqttAndroidClient.isConnected()) {
//                    Log.e(" messages in buffer.", mqttAndroidClient.getBufferedMessageCount() + "");
                }
                chatMasterUpdateUtility.updatePublishedStatus(current.getMessageID(), true);
            } catch (Exception e) {
                e.printStackTrace();
                chatMasterUpdateUtility.updatePublishedStatus(current.getMessageID(), false);
//                int a = sharedPrefs.getUnPublishedMessageCount() + 1;
//                sharedPrefs.setUnpublishedMessageCount(a);
            }

        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishUserMessage(String content, String contentType){
        MqttMessage message = new MqttMessage();

        try {
            int msgID = (int) System.currentTimeMillis();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("message_id", msgID);
            jsonObject.put("sender", sharedPrefs.getUsername());
            jsonObject.put("message", content);
            jsonObject.put("datetime", Hashdefine.getCurrentDateTimeInUTC());
            jsonObject.put("content_type", contentType);

            message.setId(msgID);
            message.setQos(0);
            message.setPayload(jsonObject.toString().getBytes());

            if (!content.equals("User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is offline")
                    && !content.equalsIgnoreCase("User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is online")) {
                ChatItemModel chatItemModel = new ChatItemModel();
                chatItemModel.setMessageID(msgID);
                chatItemModel.setContentType(contentType);
                chatItemModel.setMessage(content);
                chatItemModel.setSentDateTime(jsonObject.getString("datetime"));
                chatItemModel.setSender(sharedPrefs.getUsername());
                chatItemModel.setMessageSentStatusSuccess(false);

                if (chatMasterUpdateUtility.insert(chatItemModel) > 0) {
//                    Log.e("item insertion", "success");
                }else {
//                    Log.e("item insertion", "fail");
                }

                chatItemModelList.add(0, chatItemModel);
                chatRoomRecyclerAdapter.notifyDataSetChanged();
            }

            mqttAndroidClient.publish(Hashdefine.CHAT_PUBLISH_TOPIC, message);

            try {
                if (!mqttAndroidClient.isConnected()) {
                    Log.e(" messages in buffer.", mqttAndroidClient.getBufferedMessageCount() + "");
                }
                chatMasterUpdateUtility.updatePublishedStatus(msgID, true);
            } catch (Exception e) {
                e.printStackTrace();
                chatMasterUpdateUtility.updatePublishedStatus(msgID, false);
//                int a = sharedPrefs.getUnPublishedMessageCount() + 1;
//                sharedPrefs.setUnpublishedMessageCount(a);
            }

        } catch (MqttPersistenceException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Cursor cursor = db.rawQuery("SELECT * FROM " + ChatItemModel.TABLE_NAME + " WHERE " + ChatItemModel.KEY_IS_MESSAGE_SENT_SUCCESSFULLY + "=0 AND " + ChatItemModel.KEY_IS_MESSAGE_PUBLISHED_SUCCESSFULLY + "=1", null);
        cursor.moveToFirst();

//        Log.e("PENDING PUBLISHES", String.valueOf(cursor.getCount()));
        for(int i = 0;i<cursor.getCount(); i++) {
            chatMasterUpdateUtility.updatePublishedStatus(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_ID)), false);
//            Log.e("UPDATE RESPONSE", String.valueOf((chatMasterUpdateUtility.updatePublishedStatus(cursor.getInt(cursor.getColumnIndex(ChatItemModel.KEY_MESSAGE_ID)), false))));
        }

        cursor.close();
    }

    //    @Override
//    protected void onPause() {
//        super.onPause();
//        //send broadcast (publish message) to other users
//        publishUserMessage( "User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is offline", "alert");
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        //send broadcast (publish message) to other users
//        publishUserMessage( "User " + "\'" +  sharedPrefs.getUsername().substring(0, sharedPrefs.getUsername().lastIndexOf("_")) + "\'" + " is online", "alert");
//    }
}

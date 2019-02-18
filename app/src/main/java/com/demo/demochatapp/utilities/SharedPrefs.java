package com.demo.demochatapp.utilities;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefs {
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    Context context;
    final int PRIVATE_MODE = 0;

    private static final String PREF_NAME = "DemoChatAppPreferences";

    public SharedPrefs(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = preferences.edit();
    }

    //Username : for settings client ID
    private static final String USERNAME = "UserName";
    private static final String IS_USER_SUBSCRIBED = "IsUserSubscribed";
    private static final String IS_NEW_JOINEE_BROADCASTED = "IsNewJoineeBroadCasted";
//    private static final String UNPUBLISHED_MESSAGE_COUNT = "UnPublishedMessageCount";
//

    //SETTERS
    public void setUsername(String userName) {
        editor.putString(USERNAME, userName);
        editor.commit();
    }

    public void setIsUserSubscribed(boolean isUserSubscribed){
        editor.putBoolean(IS_USER_SUBSCRIBED, isUserSubscribed);
        editor.commit();
    }

    public void setIsNewJoineeBroadcasted(boolean isNewJoineeBroadcasted) {
        editor.putBoolean(IS_NEW_JOINEE_BROADCASTED, isNewJoineeBroadcasted);
        editor.commit();
    }

//    public void setUnpublishedMessageCount(int count) {
//        editor.putInt(UNPUBLISHED_MESSAGE_COUNT, count);
//        editor.commit();
//    }

    //GETTERS
    public String getUsername() {
        return preferences.getString(USERNAME, "");
    }

    public boolean getIsUserSubscribed(){
        return preferences.getBoolean(IS_USER_SUBSCRIBED, false);
    }

    public boolean getIsNewJoineeBroadCasted() {
        return preferences.getBoolean(IS_NEW_JOINEE_BROADCASTED, false);
    }

//    public int getUnPublishedMessageCount() {
//        return preferences.getInt(UNPUBLISHED_MESSAGE_COUNT, 0);
//    }
}

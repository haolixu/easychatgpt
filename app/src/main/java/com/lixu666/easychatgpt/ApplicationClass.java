package com.lixu666.easychatgpt;
import com.onesignal.OneSignal;

import android.app.Application;
import com.sigmob.windad.WindAdOptions;
import com.sigmob.windad.WindAds;
import com.sigmob.windad.WindAgeRestrictedUserStatus;
import com.sigmob.windad.WindConsentStatus;
public class ApplicationClass extends Application {
    private static final String ONESIGNAL_APP_ID = "e2329038-4041-4a23-8084-3163ba756cbd";
    // OneSignal Initialization应用内推送
    @Override
    public void onCreate() {
        super.onCreate();
        // OneSignal Initialization应用内推送
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.initWithContext(this);
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.promptForPushNotifications();
    }
    private void initSDK() {

        WindAds ads = WindAds.sharedAds();


        ads.setPersonalizedAdvertisingOn(true);//是否开启个性化推荐接口
        ads.setIsAgeRestrictedUser(WindAgeRestrictedUserStatus.NO);//coppa//是否年龄限制
        ads.setUserGDPRConsentStatus(WindConsentStatus.ACCEPT);//是否接受gdpr协议

        ads.startWithOptions(this, new WindAdOptions(Constants.app_id, Constants.app_key));
    }
}
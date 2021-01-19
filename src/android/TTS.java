package com.wordsbaking.cordova.tts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.HashMap;
import java.util.Locale;
import java.util.*;

import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.annotation.RequiresApi;

/*
    Cordova Text-to-Speech Plugin
    https://github.com/vilic/cordova-plugin-tts

    by VILIC VANE
    https://github.com/vilic

    updated by SEBASTIAAN PASMA
    https://github.com/spasma

    MIT License
*/

public class TTS extends CordovaPlugin implements OnInitListener {

    public static final String ERR_INVALID_OPTIONS = "ERR_INVALID_OPTIONS";
    public static final String ERR_NOT_INITIALIZED = "ERR_NOT_INITIALIZED";
    public static final String ERR_ERROR_INITIALIZING = "ERR_ERROR_INITIALIZING";
    public static final String ERR_UNKNOWN = "ERR_UNKNOWN";

    boolean ttsInitialized = false;
    TextToSpeech tts = null;
    Context context = null;

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        context = cordova.getActivity().getApplicationContext();
        tts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this, "com.google.android.tts");
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                // do nothing
            }

            @Override
            public void onDone(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.success();
                }
            }

            @Override
            public void onError(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.error(ERR_UNKNOWN);
                }
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if (action.equals("speak")) {
            speak(args, callbackContext);
        } else if (action.equals("stop")) {
            stop(args, callbackContext);
        } else if (action.equals("checkLanguage")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                checkLanguage(args, callbackContext);
            }
        } else if (action.equals("openInstallTts")) {
            callInstallTtsActivity(args, callbackContext);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onInit(int status) {
        System.out.println("TTS: tts STARTED");
        if (status != TextToSpeech.SUCCESS) {
            tts = null;
            System.out.println("TTS: NO SUCCESS");
        } else {
            // warm up the tts engine with an empty string
            HashMap<String, String> ttsParams = new HashMap<String, String>();
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
            tts.setLanguage(new Locale("en", "US"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak("",TextToSpeech.QUEUE_FLUSH,null,null);
            } else {
                tts.speak("", TextToSpeech.QUEUE_FLUSH, null);
            }
//            tts.speak("", TextToSpeech.QUEUE_FLUSH, ttsParams);
            System.out.println("TTS: SUCCESS");
            ttsInitialized = true;
        }
    }

    private void stop(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        tts.stop();
    }

    private void callInstallTtsActivity(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {

        PackageManager pm = context.getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity( installIntent, PackageManager.MATCH_DEFAULT_ONLY );

        if( resolveInfo == null ) {
            // Not able to find the activity which should be started for this intent
        } else {
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void checkLanguage(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Set<Locale> supportedLanguages = tts.getAvailableLanguages();
        String languages = "";
        if(supportedLanguages!= null) {
            for (Locale lang : supportedLanguages) {
                languages = languages + "," + lang;
            }
        }
        if (languages != "") {
            languages = languages.substring(1);
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK, languages);
        callbackContext.sendPluginResult(result);
    }

    private void speak(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        JSONObject params = args.getJSONObject(0);

        if (params == null) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        }

        String text;
        String locale;
        double rate;
        double pitch;
        boolean cancel = false;
        String voiceURI;

        if (params.isNull("text")) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        } else {
            text = params.getString("text");
        }

        if (params.isNull("locale")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                locale = Locale.getDefault().toLanguageTag();
            } else {
                locale = Locale.US.toString();
            }
        } else {
            locale = params.getString("locale");
        }

        if (!params.isNull("cancel")) {
            cancel = params.getBoolean("cancel");
        }
        Log.v("TTS", "cancel is set to "+cancel+ "("+(cancel?"TextToSpeech.QUEUE_FLUSH":"TextToSpeech.QUEUE_ADD")+")");

        if (params.isNull("rate")) {
            rate = 1.0;
            Log.v("TTS", "No rate provided, so rate is set to "+rate);
        } else {
            rate = params.getDouble("rate");
            Log.v("TTS", "rate is set to "+rate);
        }

        if (params.isNull("pitch")) {
            pitch = 1.0;
            Log.v("TTS", "No pitch provided, so pitch set to "+pitch);
        } else {
            pitch = params.getDouble("pitch");
            Log.v("TTS", "Pitch set to "+pitch);
        }

        if (tts == null) {
            callbackContext.error(ERR_ERROR_INITIALIZING);
            return;
        }

        if (!ttsInitialized) {
            callbackContext.error(ERR_NOT_INITIALIZED);
            return;
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());

        String[] localeArgs = locale.split("-");
        if(localeArgs.length == 2) {
            tts.setLanguage(new Locale(localeArgs[0], localeArgs[1]));
        } else {
            tts.setLanguage(new Locale(localeArgs[0]));
        }
        
        Voice voice = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Saq: is this even used anywhere?
            Set<Voice> voices = tts.getVoices();

            for (Voice tmpVoice : tts.getVoices()) {
                if (tmpVoice.getName().toLowerCase().contains(locale.toLowerCase())) {
                    Log.v("TTS", "Found Voice for locale: " + tmpVoice.getName());
                    voice = tmpVoice;
                    break;
                } else {
                    voice = null;
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 27) {
            tts.setSpeechRate((float) rate * 0.7f);
        } else {
            tts.setSpeechRate((float) rate);
        }
        tts.setPitch((float)pitch);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text,cancel?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,callbackContext.getCallbackId());
        } else {
            tts.speak(text,cancel?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,ttsParams);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void getVoices(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {

        Voice voice = null;

        Set<Voice> voices = tts.getVoices();
        for (Voice tmpVoice : tts.getVoices()) {
            Log.v("TTS", "Voice: "+tmpVoice.getName());
            if (tmpVoice.getName().contains("#male") && tmpVoice.getName().contains("en-us")) {
                voice = tmpVoice;
                break;
            }
            else {
                voice = null;
            }
        }
        if (voice != null) {
            tts.setVoice(voice);
        }


        final PluginResult result = new PluginResult(PluginResult.Status.OK, "done");
        callbackContext.sendPluginResult(result);
    }
}

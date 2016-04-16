package com.example.bel.softwarefactory.ui.fragments;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bel.softwarefactory.R;
import com.example.bel.softwarefactory.api.Api;
import com.example.bel.softwarefactory.api.ProgressRequestBody;
import com.example.bel.softwarefactory.preferences.UserLocalStore;
import com.example.bel.softwarefactory.utils.AppConstants;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

@EFragment(R.layout.fragment_record)
public class RecordFragment extends BaseFragment implements ProgressRequestBody.UploadCallbacks {

    private final String TAG = this.getClass().getSimpleName();
    private ProgressDialog progressDialog;

    @ViewById
    protected RelativeLayout parent_layout;
    @ViewById
    protected TextView seconds_textView;
    @ViewById
    protected TextView minutes_textView;
    @ViewById
    protected TextView hours_textView;

    @ViewById
    protected LinearLayout shareEditDelete_layout;

    private MediaPlayer mediaPlayer;
    private MediaRecorder recorder;
    private RecordButton sfRecordButton;
    private PlayRecordingButton sfPlayButton;

    @Bean
    protected UserLocalStore userLocalStore;

    private final File EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory();

    protected String recordName = "audio";

    private final String RECORD_TAG = "Debug_Record";

    @AfterViews
    protected void afterViews() {
//        userLocalStore = new UserLocalStore(getActivity());
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setTitle("Record ambient sounds");
        }
        createLayout();
    }

    @Click(R.id.deleteRecord_button)
    protected void deleteRecord_button_click() {
        //Delete this file
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
        alertBuilder.setTitle(R.string.is_delete_file);
        alertBuilder.setCancelable(true);
        alertBuilder.setPositiveButton(R.string.delete_record, (dialog, which) -> {
            File file = new File(getRecordPath());
            file.delete();
            shareEditDelete_layout.setVisibility(View.GONE);
            fillTimer("0", "0", "0");
        });
        alertBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            dialog.dismiss();
        });
        alertBuilder.show();
    }

    @Click(R.id.shareRecord_button)
    protected void shareRecord_button_click() {
        File outputFile = new File(getRecordPath());
        if (outputFile.exists()) {
            String owner = userLocalStore.isFacebookLoggedIn() ? userLocalStore.getFacebookId() + "" : userLocalStore.getEmail();
            Log.d(RECORD_TAG, "uploadRecordingToServer for owner " + owner);

            //Change fragment to map in order to get the location
            switchFragment(MapFragment_.builder().build());
            uploadFile(outputFile, owner);
        }
    }

    @Click(R.id.editRecord_button)
    public void editRecord_button_click() {
        File file = new File(getRecordPath());
        Log.d(RECORD_TAG, getRecordPath());
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
        alertBuilder.setTitle(R.string.is_edit_file);
        alertBuilder.setCancelable(true);

        final EditText etChangeRecordName = new EditText(getActivity());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        etChangeRecordName.setLayoutParams(params);
        etChangeRecordName.setText(file.getName());
        alertBuilder.setView(etChangeRecordName);

        alertBuilder.setPositiveButton(R.string.edit_record, (dialog, which) -> {
            String newRecordName = etChangeRecordName.getText().toString();
            if (newRecordName.isEmpty())
                dialog.dismiss();

            newRecordName = newRecordName.replace(AppConstants.AUDIO_EXTENSION, "");

            File from = new File(EXTERNAL_STORAGE_PATH, recordName + AppConstants.AUDIO_EXTENSION);
            File to = new File(EXTERNAL_STORAGE_PATH, newRecordName + AppConstants.AUDIO_EXTENSION);

            if (from.exists()) {
                from.renameTo(to);
                recordName = newRecordName;
            }
        });
        alertBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            dialog.dismiss();
        });
        alertBuilder.show();
    }

    //Create buttons and layout
    public void createLayout() {

        //create button with changeable images
        sfRecordButton = new RecordButton(getContext());
        //set ID defined in ids.xml file
        sfRecordButton.setId(R.id.RecordButton);
        //set picture background to transparent
        //sfRecordButton.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        sfRecordButton.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));

        // set layout parameters for button
        RelativeLayout.LayoutParams paramsRecord = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        //add button BELOW + centralizing last button with name Stop Play audio
        paramsRecord.addRule(RelativeLayout.CENTER_IN_PARENT);
        paramsRecord.addRule(RelativeLayout.BELOW, R.id.Timer);
        //add button for Recording to the layout + parameters
        parent_layout.addView(sfRecordButton, paramsRecord);

//        //create button with changeable images
//        sfStopButton = new StopButton(getContext());
//        //set ID defined in ids.xml file
//        sfStopButton.setId(R.id.StopButton);
//        //set picture background to transparent
//        sfStopButton.setBackgroundColor(getResources().getColor(android.R.color.transparent));

        // set layout parameters for button
        RelativeLayout.LayoutParams paramsStop = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        //add button BELOW + centralizing last button with name Stop Play audio
        paramsStop.addRule(RelativeLayout.LEFT_OF, sfRecordButton.getId());
        paramsStop.addRule(RelativeLayout.BELOW, R.id.Timer);
        paramsStop.addRule(RelativeLayout.CENTER_HORIZONTAL);
        //add button for Recording to the layout + parameters
        //parent_layout.addView(sfStopButton, paramsStop);

        //create button with changeable images
        sfPlayButton = new PlayRecordingButton(getContext());
        //set picture background to transparent
        sfPlayButton.setBackgroundColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));

        // set layout parameters for button
        RelativeLayout.LayoutParams paramsPlay = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        //add button BELOW + centralizing last button with name Stop Play audio
        paramsPlay.addRule(RelativeLayout.RIGHT_OF, sfRecordButton.getId());
        paramsPlay.addRule(RelativeLayout.BELOW, R.id.Timer);
        paramsPlay.addRule(RelativeLayout.CENTER_HORIZONTAL);
        //add button for Recording to the layout + parameters
        parent_layout.addView(sfPlayButton, paramsPlay);
    }

    class RecordButton extends ImageButton {
        private boolean isRecording;
        private MessageHandler messageHandler = new MessageHandler();
        private int curTime;
        private boolean isRecordSaved = false;

        public RecordButton(Context ctx) {
            super(ctx);
            setImageResource(R.mipmap.ic_rec);
            setOnClickListener(clicker);
            setRecording(true);
        }

        public void setRecording(boolean recording) {
            this.isRecording = recording;
        }

        public boolean getRecording() {
            return isRecording;
        }

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                //function to check recording
                if (isRecording) {
                    setRecording(!isRecording);
                    //change the image to stop
                    setImageResource(R.mipmap.ic_rec_stop);
                    //Start recording the sound
                    StartRecord();
                    sfPlayButton.disable();
                } else {
                    setRecording(!isRecording);
                    //change image to recording
                    setImageResource(R.mipmap.ic_rec);
                    //function to Stop recording
                    StopRecord();
                    sfPlayButton.enable();
                }
                Log.d(RECORD_TAG, "RecordButton onClick() isRecording : " + isRecording);
            }
        };

        public void StartRecord() {
            ditchMediaRecorder();
            /*
            * Check if there is existing file, then delete
            * */
            File outputFile = new File(getRecordPath());
            if (outputFile.exists()) {
                outputFile.delete();
            }
            //initialize file name of the time when the recording is done
            recordName = getCurrentDate();

            /*
            * Initialize media recorder and start recording
            * */
            recorder = new MediaRecorder();
            recorder.setAudioSource(AppConstants.AUDIO_SOURCE);
            recorder.setOutputFormat(AppConstants.OUTPUT_FORMAT);
            recorder.setAudioEncoder(AppConstants.AUDIO_ENCODER);
            recorder.setOutputFile(getRecordPath());
            try {
                recorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            recorder.start();
            startTimerCounting();
            shareEditDelete_layout.setVisibility(View.GONE);
        }

        public void ditchMediaRecorder() {
            if (recorder != null) recorder.release();
        }

        public void StopRecord() {
            if (recorder != null) {
                recorder.reset();
                recorder.release();
                recorder = null;
            }
            //show additional menu for editing file
            shareEditDelete_layout.setVisibility(View.VISIBLE);
            editRecord_button_click();
        }

        public void startTimerCounting() {
            curTime = 0;
            fillTimer("0", "0", "0");
            Thread thread = new Thread(new TimerRecord());
            thread.start();
        }

        public void enable() {
            setEnabled(true);
            setAlpha(1f);
        }

        public void disable() {
            setEnabled(false);
            setAlpha(0.5f);
        }

        private class TimerRecord implements Runnable {
            @Override
            public void run() {
                Log.d(RECORD_TAG, "TimeRecord run() isRecording" + getRecording());
                while (!getRecording()) {
                    ++curTime;
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Bundle bundle = new Bundle();
                    bundle.putInt("count", curTime);

                    Message message = new Message();
                    message.setData(bundle);
                    messageHandler.sendMessage(message);
                }
                Bundle bundle = new Bundle();
                bundle.putInt("count", --curTime);
                Message message = new Message();
                message.setData(bundle);
                messageHandler.sendMessage(message);
            }
        }

        // Handler for Runnable class + Timer filling
        private class MessageHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                convertSecondsToTime(msg.getData().getInt("count"));
            }

            public void convertSecondsToTime(int duration) {
                String hours = String.valueOf(duration / 3600);
                String minutes = String.valueOf((duration % 3600) / 60);
                String seconds = String.valueOf((duration % 3600) % 60);
                fillTimer(hours, minutes, seconds);
            }
        }

    }

    class PlayRecordingButton extends ImageButton {
        boolean sfPlayRecording = true;

        public PlayRecordingButton(Context ctx) {
            super(ctx);
            setImageResource(R.mipmap.ic_rec_play);
            setOnClickListener(clicker);
            setVisibility(View.GONE);
        }

        OnClickListener clicker = v -> {
            //function to check recording
            if (sfPlayRecording) {
                setImageResource(R.mipmap.ic_rec_pause);
                try {
                    PlayRecord();
                    sfRecordButton.disable();
                    //sfStopButton.enable();
//                        Toast toast = Toast.makeText(getContext(), "Playing the audio...", Toast.LENGTH_LONG);
//                        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
//                        toast.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                setImageResource(R.mipmap.ic_rec_play);
                PausePlay();
                sfRecordButton.enable();
                //sfStopButton.disable();
            }
            sfPlayRecording = !sfPlayRecording;
        };

        public void PlayRecord() throws IOException {
            ditchMediaPlayer();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.start();
            try {
                mediaPlayer.setDataSource(getRecordPath());
                //mediaPlayer.setOnCompletionListener(getContext());
                mediaPlayer.prepare();
                mediaPlayer.start();
                fillTimer("0", "0", "0");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void ditchMediaPlayer() {
            if (mediaPlayer != null) mediaPlayer.release();
        }

        public void PausePlay() {
            if (mediaPlayer != null) mediaPlayer.pause();
        }

        public void StopPlay() {
            if (mediaPlayer != null) mediaPlayer.stop();
        }

        public void enable() {
            setVisibility(View.VISIBLE);
            //setEnabled(true);
            //setAlpha(1f);
        }

        public void disable() {
            setVisibility(View.GONE);
            //setEnabled(false);
            //setAlpha(0.5f);
        }

    }

//    class StopButton extends ImageButton {
//        //boolean sfStartRecording = true;
//        int whatToStop;
//
//        public StopButton(Context ctx) {
//            super(ctx);
//            setImageResource(R.mipmap.ic_rec_stop);
//            setOnClickListener(clicker);
//            disable();
//        }
//
//        OnClickListener clicker = new OnClickListener() {
//            public void onClick(View v) {
//                if (getWhatToStop() == 1)
//                    StopRecord();
//                else if (getWhatToStop() == 2)
//                    StopPlay();
//            }
//        };
//
//        public void enable() {
//            setEnabled(true);
//            setAlpha(1f);
//        }
//
//        public void disable() {
//            setEnabled(false);
//            setAlpha(0.5f);
//        }
//
//        public void setWhatToStop(int whatToStop) {
//            this.whatToStop = whatToStop;
//            enable();
//        }
//
//        public int getWhatToStop() {
//            return whatToStop;
//        }
//    }

    // functions for MEDIA RECORDING

    //finish of MEDIA RECORDING functions

    // functions for MEDIA PLAYER

    //Start timer functions

    public void fillTimer(String h, String m, String s) {
        seconds_textView.setText(s.length() == 1 ? "0" + s : s);
        minutes_textView.setText(m.length() == 1 ? "0" + m : m);
        hours_textView.setText(h.length() == 1 ? "0" + h : h);
    }

    //get file duration
    //code taken from http://stackoverflow.com/questions/15394640/get-duration-of-audio-file
    public long getDuration(String dataSource) {
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        metaRetriever.setDataSource(dataSource);
        String durationString =
                metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long durationLong = Long.parseLong(durationString);
        convertMillisecondsToTime(durationLong);
        //Toast.makeText(getContext(), durationString, Toast.LENGTH_LONG).show();
        return durationLong;
    }

    public void convertMillisecondsToTime(long duration) {
        String hours = String.valueOf(((duration / (1000 * 60 * 60)) % 24));
        String minutes = String.valueOf((duration / (1000 * 60)) % 60);
        String seconds = String.valueOf((duration / 1000) % 60);
        fillTimer(hours, minutes, seconds);
    }

    //get the path of the record if the name was changed, path for already recorded sound and future changes
    private String getRecordPath() {
        return EXTERNAL_STORAGE_PATH + File.separator + recordName + AppConstants.AUDIO_EXTENSION;
    }

    private String getCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.DAY_OF_MONTH) + "_" + calendar.get(Calendar.MONTH) + "_"
                + calendar.get(Calendar.YEAR) + "_" + calendar.get(Calendar.HOUR) + ":" + calendar.get(Calendar.MINUTE) + ":"
                + calendar.get(Calendar.SECOND);
    }

    /* private methods */

    private void uploadFile(File file, String owner) {
        Log.d(RECORD_TAG, "uploadRecordingToServer");
        String latitude = userLocalStore.getLastLatitude();
        String longitude = userLocalStore.getLastLongitude();

        if (latitude.isEmpty() && longitude.isEmpty()) {
            Toast.makeText(getActivity(), "Impossible to save file. Possibly GPS is not enabled.", Toast.LENGTH_LONG).show();
            return;
        }

        ProgressRequestBody requestFile = new ProgressRequestBody(file, this);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), requestFile)
                .addFormDataPart("owner", owner)
                .addFormDataPart("latitude", latitude)
                .addFormDataPart("longitude", longitude)
                .build();

        Log.d(TAG, "Owner : " + owner);
        Log.d(TAG, "Latitude : " + latitude);
        Log.d(TAG, "Longitude : " + longitude);

        Api api = new Api();
        api.upload(requestBody)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(bindToLifecycle())
                .doOnError(this::handleError)
                .subscribe(this::uploadFinished, this::handleError);
    }

    private void uploadFinished(ResponseBody responseBody) {
        Log.d(TAG, "uploadFinished()");
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        Log.d(TAG, responseBody.toString());
    }

    @Override
    public void onProgressUpdate(int percentage) {
        Log.d(TAG, "onProgressUpdate()");
        if (progressDialog != null) {
            progressDialog.setProgress(percentage);
        } else {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.setMax(100);
            progressDialog.setProgress(percentage);
            progressDialog.show();
        }
    }

}

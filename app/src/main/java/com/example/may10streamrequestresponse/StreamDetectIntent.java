package com.example.may10streamrequestresponse;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.BidiStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.dialogflow.cx.v3beta1.AudioEncoding;
import com.google.cloud.dialogflow.cx.v3beta1.AudioInput;
import com.google.cloud.dialogflow.cx.v3beta1.AudioInputOrBuilder;
import com.google.cloud.dialogflow.cx.v3beta1.DetectIntentRequest;
import com.google.cloud.dialogflow.cx.v3beta1.DetectIntentResponse;
import com.google.cloud.dialogflow.cx.v3beta1.InputAudioConfig;
import com.google.cloud.dialogflow.cx.v3beta1.OutputAudioConfig;
import com.google.cloud.dialogflow.cx.v3beta1.OutputAudioEncoding;
import com.google.cloud.dialogflow.cx.v3beta1.QueryInput;
import com.google.cloud.dialogflow.cx.v3beta1.QueryParameters;
import com.google.cloud.dialogflow.cx.v3beta1.QueryResult;
import com.google.cloud.dialogflow.cx.v3beta1.ResponseMessage;
import com.google.cloud.dialogflow.cx.v3beta1.SessionName;
import com.google.cloud.dialogflow.cx.v3beta1.SessionsClient;
import com.google.cloud.dialogflow.cx.v3beta1.SessionsSettings;
import com.google.cloud.dialogflow.cx.v3beta1.StreamingDetectIntentRequest;
import com.google.cloud.dialogflow.cx.v3beta1.StreamingDetectIntentResponse;
import com.google.cloud.dialogflow.cx.v3beta1.TextInput;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;

import io.grpc.stub.StreamObserver;

public class StreamDetectIntent  implements StreamObserver<StreamingDetectIntentResponse> {

    private static String TAG = "DetectIntent";

    Context context;
    String projectId, locationId, agentId, sessionId;

    public StreamDetectIntent(){

        projectId = "xxxxx";
        locationId= "us-xxxx";
        agentId = "xxxxxx";
        sessionId = UUID.randomUUID().toString();
    }

    public void detectIntent(
            String audioFilePath,
            Context context)
            throws IOException, ApiException {

        this.context = context;

        SessionsSettings.Builder sessionsSettingsBuilder = SessionsSettings.newBuilder();

        if (locationId.equals("global")) {
            sessionsSettingsBuilder.setEndpoint("dialogflow.googleapis.com:443");
        } else {
            sessionsSettingsBuilder.setEndpoint(locationId + "-dialogflow.googleapis.com:443");
        }

        InputStream stream = context.getResources().openRawResource(R.raw.credentials);
        GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));

        SessionsSettings sessionsSettings = sessionsSettingsBuilder.setCredentialsProvider(
                FixedCredentialsProvider.create(credentials)).build();

//        Map<String, QueryResult> queryResults = Maps.newHashMap();

        // Instantiates a client.
        // Note: close() needs to be called on the SessionsClient object to clean up resources
        // such as threads. In the example below, try-with-resources is used,
        // which automatically calls close().
        try (SessionsClient sessionsClient = SessionsClient.create(sessionsSettings)) {

            SessionName session = SessionName.of(projectId,locationId,agentId,sessionId);

            Log.e(TAG, session.toString());

            // Instructs the speech recognizer how to process the audio content.
            // Note: hard coding audioEncoding and sampleRateHertz for simplicity.
            // Audio encoding of the audio content sent in the query request.
            InputAudioConfig inputAudioConfig =
                    InputAudioConfig.newBuilder()
                            .setAudioEncoding(AudioEncoding.AUDIO_ENCODING_AMR_WB)
                            .setSampleRateHertz(16000)
                            .build();

            OutputAudioConfig outputAudioConfig = OutputAudioConfig.newBuilder()
                    .setAudioEncoding(OutputAudioEncoding.OUTPUT_AUDIO_ENCODING_LINEAR_16)
                    .setSampleRateHertz(16000)
                    .build();

            //converting our audio file int byteArray
            byte[] audioData = convertAudioToByteArray(audioFilePath);

            AudioInput audioInput = AudioInput.newBuilder()
                    .setConfig(inputAudioConfig)
                    .setAudio(ByteString.copyFrom(audioData))
                    .build();

            // Build the query with the InputAudioConfig
            QueryInput queryInput = QueryInput.newBuilder()
                    .setAudio(audioInput).build();

            // Create the Bidirectional stream
            BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream =
                    sessionsClient.streamingDetectIntentCallable().call();

            // The first request must **only** contain the audio configuration:
            bidiStream.send(
                    StreamingDetectIntentRequest.newBuilder()
                            .setSession(session.toString())
                            .setOutputAudioConfig(outputAudioConfig)
                            .setQueryInput(QueryInput.newBuilder().setLanguageCode("en-US").setAudio(AudioInput.newBuilder().setConfig(inputAudioConfig).build()))
                            .build());


            //The second request sending audio data:
            bidiStream.send(
                    StreamingDetectIntentRequest.newBuilder()
                            .setQueryInput(queryInput)
                            .build());

            // Tell the service you are done sending data
            bidiStream.closeSend();

            for (StreamingDetectIntentResponse response : bidiStream) {

                if(response.getDetectIntentResponse().getQueryResult().hasTranscript()){
                    Log.e("RESPONSE", "-----GOT Transcript-----");
                    Log.e("RESPONSE", response.getDetectIntentResponse().getQueryResult().getTranscript());
                }

                //listeners from old code
                this.onNext(response);
                this.onCompleted();

            }

        }
    }

    @Override
    public void onNext(StreamingDetectIntentResponse value) {

        if(!value.getDetectIntentResponse().getQueryResult().getResponseMessagesList()
                .isEmpty()
        ){
            Log.e("Response", "onNext - response Received "+value.getDetectIntentResponse().getQueryResult().getResponseMessages(0));

            if(value.getDetectIntentResponse().hasOutputAudioConfig()){
                ByteString audioBytes = value.getDetectIntentResponse().getOutputAudio();
                Log.e("ResponseAudio","Playing from my if");
                // Play the audio bytes using Android MediaPlayer or other audio playback mechanism
                playAudioBytes(audioBytes);
            }
        }else {
            Log.e("Response", "onNext - response Received is Empty");
        }

    }

    @Override
    public void onError(Throwable t) {
        Log.e("Response", "onNext - response onError: "+t.getMessage());
    }

    @Override
    public void onCompleted() {
        Log.e("Response", "onNext - response completed");
    }

    //using when sending our request
    private static byte[] convertAudioToByteArray(String filePath) throws IOException {
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream byteOutputStream = null;

        try {
            fileInputStream = new FileInputStream(filePath);
            byteOutputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteOutputStream.write(buffer, 0, bytesRead);
            }

            return byteOutputStream.toByteArray();
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }

            if (byteOutputStream != null) {
                byteOutputStream.close();
            }
        }
    }

    // Method to play audio bytes using Android MediaPlayer
    private void playAudioBytes(ByteString audioBytes) {
        try {
            MediaPlayer mediaPlayer;
            byte[] audioData = audioBytes.toByteArray();

            // Write audio data to a temporary file
            String tempAudioFilePath = getTempAudioFilePath();
            writeAudioDataToFile(audioData, tempAudioFilePath);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(tempAudioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // Audio playback completed
                    Log.e("ResponseAudio","...is finished");
                    cleanupTempAudioFile(tempAudioFilePath);

                    //here we can start recording again
                    MainActivity.startRecording();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to write audio data to a temporary file
    private void writeAudioDataToFile(byte[] audioData, String filePath) throws IOException {
        FileOutputStream fos = new FileOutputStream(filePath);
        fos.write(audioData);
        fos.close();
    }

    // Method to generate a temporary file path for storing audio data
    private String getTempAudioFilePath() {
        String cacheDir = context.getCacheDir().getAbsolutePath();
        return cacheDir + "/temp_audio_file.wav"; // Adjust the file extension if needed
    }

    // Method to clean up the temporary audio file
    private void cleanupTempAudioFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }
}

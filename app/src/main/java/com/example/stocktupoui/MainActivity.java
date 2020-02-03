package com.example.stocktupoui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import android.os.Bundle;
//
import java.net.URI;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.*;
//
import android.view.KeyEvent;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.Context;
import android.content.SharedPreferences;
//
import androidx.core.app.RemoteInput;
//
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

//
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //
        //if (savedInstanceState!=null)
        //{
        //    clientProvideName = savedInstanceState.getString(Constants.CLIENT_ID);
        //    filter            = savedInstanceState.getString(Constants.FILTER_ID);
        //}
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.DEFAULT_CLIENT_ID, Context.MODE_PRIVATE);
        if (sharedPreferences!=null)
        {
            clientProvideName = sharedPreferences.getString(Constants.CLIENT_ID,Constants.DEFAULT_CLIENT_ID);
            filter            = sharedPreferences.getString(Constants.FILTER_ID,Constants.DEFAULT_FILTER_ID);
            comments          = sharedPreferences.getString(Constants.COMMENTS_ID,Constants.DEFAULT_COMMENTS_ID);
        }
        //if ( clientProvideName=="")
        //{
        //    clientProvideName = Constants.DEFAULT_CLIENT_ID;
        //}
        //
        initControl();
        //
        init_channel();
        //
        send_notification("onCreate",String.format("%d:%s:%s",getTaskId(),clientProvideName,filter));
        //
        setupConnectionFactory();
        //
    }
    @Override
    protected void onStart() {
        super.onStart();
        // The activity is about to become visible.
        //send_notification("onStart",String.format("%d",getTaskId()));
    }
    @Override
    protected void onResume() {
        super.onResume();
        // The activity has become visible (it is now "resumed").
        //send_notification("onResume",String.format("%d",getTaskId()));
    }
    @Override
    protected void onPause() {
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
        //send_notification("onPause",String.format("%d",getTaskId()));
    }
    @Override
    protected void onStop() {
        super.onStop();
        // The activity is no longer visible (it is now "stopped")
        //send_notification("onStop",String.format("%d",getTaskId()));
    }
    //
    @Override
    protected void onDestroy() {
        send_notification("onDestroy",String.format("%d",getTaskId()));
        try {
            if (readChannel != null) {
                readChannel.close();
                readChannel = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
            if (connectionThread!=null)
            {
                connectionThread.interrupt();
                connectionThread = null;
            }
        }
        catch (Exception e)
        {
            e.getMessage();
            e.printStackTrace();
        }
        super.onDestroy();
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        int orientation = newConfig.orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            // TODO: 18/2/22 竖屏操作
        }
       else if (orientation == Configuration.ORIENTATION_LANDSCAPE)
       {
           // TODO: 18/2/22 横屏操
       }
       //send_notification("onConfigurationChanged",String.format("%d",getTaskId()));
    }
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        setIntent(intent);
        //send_notification("onNewIntent",String.format("%d",getTaskId()));
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        String message = "N/A";
        if (remoteInput != null) {
            message = remoteInput.getCharSequence(Constants.KEY_TEXT_REPLY).toString();
            Log.d("twapui", message);
            //send_notification("onNewIntent.reply",message);
            final int requestNotifyId = intent.getExtras().getInt(Constants.REQUEST_NOTIFICATION_ID);
            if(publishChannel!=null)
            {
                final Map<String, Object> headers = new HashMap<String, Object>();
                headers.put(Constants.MAIL_TAG, "reply:"+intent.getExtras().getString(Constants.MAIL_TAG));
                headers.put(Constants.MAIL_SUBJECT, clientProvideName+":"+message+"\r\n"+intent.getExtras().getString(Constants.MAIL_SUBJECT));
                headers.put(Constants.MAIL_BODY, intent.getExtras().getString(Constants.MAIL_BODY));
                headers.put(Constants.REQUEST_NOTIFICATION_ID, requestNotifyId);

                publishThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            publishChannel.basicPublish(Constants.EXCHANGE_NAME, Constants.EXCHANGE_DEFAULT_ROUTE,
                                    new AMQP.BasicProperties.Builder()
                                            .headers(headers)
                                            .build(),
                                    null);
                        }
                        catch (Exception e)
                        {
                            e.getMessage();
                            e.printStackTrace();
                            Log.d("twapui", String.format("err.publishChannel.requestNotifyId:%d",requestNotifyId));
                            send_notification("onNewIntent",String.format("err.publishChannel.requestNotifyId:%d",requestNotifyId));
                        }
                    }
                });
                publishThread.start();
                Log.d("twapui", String.format("publishChannel.requestNotifyId:%d",requestNotifyId));
                //send_notification("onNewIntent",String.format("publishChannel.requestNotifyId:%d",requestNotifyId));
            }
        }
        else
        {
            //send_notification("onNewIntent",String.format("TaskId:%d:%s",getTaskId(),message));
        }

    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //
        outState.putString(Constants.CLIENT_ID,clientProvideName);
        outState.putString(Constants.FILTER_ID,filter);
        outState.putString(Constants.COMMENTS_ID,comments);
        //
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.DEFAULT_CLIENT_ID, Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();//获取编辑器
        editor.putString(Constants.CLIENT_ID,clientProvideName);
        editor.putString(Constants.FILTER_ID,filter);
        editor.putString(Constants.COMMENTS_ID,comments);
        editor.commit();//提交修改
        //
        //send_notification("onSaveInstanceState",String.format("%d:%s:%s",getTaskId(),clientProvideName,filter));
    }
    private void onMessageReceived(final String subject,final String body) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvSubject = (TextView) findViewById(R.id.id_mail_subject);
                tvSubject.setText(subject+"\r\n"+body);
                //TextView tvBody = (TextView) findViewById(R.id.id_mail_body);
                //tvBody.setText(body);
            }
        });
    }
    private void onMessageReceived(final String title) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //
                setTitle(title);
                //
            }
        });
    }
    //
    private void initControl()
    {
        EditText tvClientId = (EditText) findViewById(R.id.id_mail_client_id_content);
        tvClientId.setFocusableInTouchMode(true);
        tvClientId.setFocusable(true);
        tvClientId.requestFocus();
        tvClientId.setText(clientProvideName);
        //
        EditText tvFilter = (EditText) findViewById(R.id.id_mail_filter_content);
        tvFilter.setFocusableInTouchMode(true);
        tvFilter.setFocusable(true);
        tvFilter.requestFocus();
        tvFilter.setText(filter);
        //
        EditText tvComments = (EditText) findViewById(R.id.id_mail_comments_content);
        tvComments.setFocusableInTouchMode(true);
        tvComments.setFocusable(true);
        tvComments.requestFocus();
        tvComments.setText(comments);
    }
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.id_btn_reset:
                Log.d("twapui","R.id.id_btn_reset");
                //send_notification("onClick","R.id.id_btn_reset");
                clientProvideName = Constants.DEFAULT_CLIENT_ID;
                filter=Constants.DEFAULT_FILTER_ID;
                EditText tvClient = (EditText) findViewById(R.id.id_mail_client_id_content);
                tvClient.setText(clientProvideName);
                EditText tvFilt = (EditText) findViewById(R.id.id_mail_filter_content);
                tvFilt.setText(filter);
                break;
            case R.id.id_btn_apply:
                Log.d("twapui","R.id.id_btn_apply");
                //send_notification("onClick","R.id.id_btn_apply");
                EditText tvClientId = (EditText) findViewById(R.id.id_mail_client_id_content);
                clientProvideName = tvClientId.getText().toString();
                EditText tvFilter = (EditText) findViewById(R.id.id_mail_filter_content);
                filter = tvFilter.getText().toString();
                EditText tvComments = (EditText) findViewById(R.id.id_mail_comments_content);
                comments = tvComments.getText().toString();
                //send_notification("onClick","R.id.id_btn_apply:"+clientProvideName+":"+filter);
                SharedPreferences sharedPreferences = getSharedPreferences(Constants.DEFAULT_CLIENT_ID, Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = sharedPreferences.edit();//获取编辑器
                editor.putString(Constants.CLIENT_ID,clientProvideName);
                editor.putString(Constants.FILTER_ID,filter);
                editor.putString(Constants.COMMENTS_ID,comments);
                editor.commit();
                break;
            default:

        }
    }
    //
    String filter="";
    String clientProvideName="";
    String clientId="";
    String comments="";
    private Thread connectionThread;
    ConnectionFactory factory = new ConnectionFactory();
    private Connection connection;
    private Channel readChannel;
    private Channel publishChannel;
    private Thread  publishThread;
    //
    private static int notificationID = 1;
    private static int requestCode    =1;
    String channelID = "1";
    private void init_channel()
    {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel mChannel = new NotificationChannel(channelID, "twapui_channel", NotificationManager.IMPORTANCE_HIGH);
        //
        mChannel.setName("twapui_channel");
        mChannel.setDescription("AAAAAAAAAA");//设置渠道的描述信息
        mChannel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        //
        nm.createNotificationChannel(mChannel);
    }
    private void adaptAndroidN(Context context, NotificationCompat.Builder builder,int requestNotificationID,String tag,String subject,String body) {
        String replyLabel = "reply...";
        RemoteInput remoteInput = new RemoteInput.Builder(Constants.KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build();
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(Constants.REQUEST_NOTIFICATION_ID, requestNotificationID);
        //
        intent.putExtra(Constants.MAIL_TAG, tag);
        intent.putExtra(Constants.MAIL_SUBJECT, subject);
        intent.putExtra(Constants.MAIL_BODY, body);
        //
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode++, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, replyLabel, pendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build();
        builder.addAction(action);
    }
    /*
    private void adaptAndroidN_Broadcast(Context context, NotificationCompat.Builder builder, String user) {
        String replyLabel = "回复";
        RemoteInput remoteInput = new RemoteInput.Builder(Constants.KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build();
        Intent intent = new Intent(context, NotificationBroadcastReceiver.class);
        intent.setAction("com.twapui.stock.reply");
        intent.putExtra("userId", user);
        PendingIntent pendingIntent = PendingIntent.getService(context, requestCode++, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, replyLabel, pendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build();
        builder.addAction(action);
    }
    */
    ///带reply功能的通知
    private void send_reply_broadcast_notification(String subject,String body,String tag)
    {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //
        //准备intent
        Intent clickIntent = new Intent(this, NotificationBroadcastReceiver.class);
        clickIntent.setAction("com.twapui.stock.click");
        clickIntent.putExtra(Constants.MAIL_SUBJECT,subject);
        clickIntent.putExtra(Constants.MAIL_BODY,body);
        clickIntent.putExtra(Constants.MAIL_TAG,tag);
        // 构建 PendingIntent
        PendingIntent clickPI = PendingIntent.getBroadcast(this, requestCode++, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //准备intent
        Intent cancelIntent = new Intent(this, NotificationBroadcastReceiver.class);
        cancelIntent.setAction("com.xxx.xxx.cancel");
        // 构建 PendingIntent
        PendingIntent cancelPI = PendingIntent.getBroadcast(this, requestCode++, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT );

        //创建通知对象
        NotificationCompat.Builder n = new NotificationCompat.Builder(MainActivity.this, channelID);
        n.setContentTitle(tag);
        n.setContentText(body);
        n.setSmallIcon(R.mipmap.ic_launcher);
        n.setWhen(System.currentTimeMillis());
        //
        adaptAndroidN(this,n,notificationID,tag,subject,body);
        //
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.setBigContentTitle(tag);
        style.bigText(subject+"\r\n"+body);
        n.setStyle(style);
        //
        n.setContentIntent(clickPI);// 设置pendingIntent,点击通知时就会用到
        n.setAutoCancel(false);      // 设为true，点击通知栏移除通知
        n.setDeleteIntent(cancelPI);// 设置pendingIntent,左滑右滑通知时就会用到
        //振动手机
        nm.notify(notificationID, n.build());
        notificationID++;
    }
    ///不带reply功能的通知
    private void send_broadcast_notification(String subject,String body,String tag)
    {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //
        //准备intent
        Intent clickIntent = new Intent(this, NotificationBroadcastReceiver.class);
        clickIntent.setAction("com.twapui.stock.click");
        clickIntent.putExtra(Constants.MAIL_SUBJECT,subject);
        clickIntent.putExtra(Constants.MAIL_BODY,body);
        clickIntent.putExtra(Constants.MAIL_TAG,tag);
        // 构建 PendingIntent
        PendingIntent clickPI = PendingIntent.getBroadcast(this, requestCode++, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //准备intent
        Intent cancelIntent = new Intent(this, NotificationBroadcastReceiver.class);
        cancelIntent.setAction("com.xxx.xxx.cancel");
        // 构建 PendingIntent
        PendingIntent cancelPI = PendingIntent.getBroadcast(this, requestCode++, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT );

        //创建通知对象
        NotificationCompat.Builder n = new NotificationCompat.Builder(MainActivity.this, channelID);
        n.setContentTitle(tag);
        n.setContentText(body);
        n.setSmallIcon(R.mipmap.ic_launcher);
        n.setWhen(System.currentTimeMillis());
        //
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.setBigContentTitle(tag);
        style.bigText(subject+"\r\n"+body);
        n.setStyle(style);
        //
        n.setContentIntent(clickPI);// 设置pendingIntent,点击通知时就会用到
        n.setAutoCancel(false);      // 设为true，点击通知栏移除通知
        n.setDeleteIntent(cancelPI);// 设置pendingIntent,左滑右滑通知时就会用到
        //振动手机
        nm.notify(notificationID, n.build());
        notificationID++;
    }
    private void send_reply_broadcast_notification(String subject,String body,String tag,int requestNotificationID)
    {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //
        //准备intent
        Intent clickIntent = new Intent(this, NotificationBroadcastReceiver.class);
        clickIntent.setAction("com.twapui.stock.click");
        clickIntent.putExtra(Constants.MAIL_SUBJECT,subject);
        clickIntent.putExtra(Constants.MAIL_BODY,body);
        clickIntent.putExtra(Constants.MAIL_TAG,tag);
        // 构建 PendingIntent
        PendingIntent clickPI = PendingIntent.getBroadcast(this, requestCode++, clickIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        //准备intent
        Intent cancelIntent = new Intent(this, NotificationBroadcastReceiver.class);
        cancelIntent.setAction("com.xxx.xxx.cancel");
        // 构建 PendingIntent
        PendingIntent cancelPI = PendingIntent.getBroadcast(this, requestCode++, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT );

        //创建通知对象
        NotificationCompat.Builder n = new NotificationCompat.Builder(MainActivity.this, channelID);
        n.setContentTitle(tag);
        n.setContentText(body);
        n.setSmallIcon(R.mipmap.ic_launcher);
        n.setWhen(System.currentTimeMillis());
        //
        adaptAndroidN(this,n,requestNotificationID,tag,subject,body);
        //
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.setBigContentTitle(tag);
        style.bigText(subject+"\r\n"+body);
        n.setStyle(style);
        //
        n.setContentIntent(clickPI);// 设置pendingIntent,点击通知时就会用到
        n.setAutoCancel(false);      // 设为true，点击通知栏移除通知
        n.setDeleteIntent(cancelPI);// 设置pendingIntent,左滑右滑通知时就会用到
        //振动手机
        nm.notify(requestNotificationID, n.build());
    }
    private void send_notification(String subject,String body)
    {
        send_reply_broadcast_notification(subject,body,subject);
        return;
        /*
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //创建通知对象
        NotificationCompat.Builder n = new NotificationCompat.Builder(MainActivity.this, channelID);
        n.setContentTitle(subject);
        n.setContentText(body);
        n.setSmallIcon(R.mipmap.ic_launcher);
        n.setWhen(System.currentTimeMillis());
        //振动手机
        nm.notify(notificationID, n.build());
        notificationID++;
         */
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK){
            moveTaskToBack(true);
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }
    //
    private void setupConnectionFactory() {

        connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //
                    //send_notification("setupConnectionFactory.run",String.format("%d",getTaskId()));
                    //
                    //clientProvideName ="18017162448";
                    String amqpuri    = "amqp://covprbbm:9exUT3KY19XpOfngiwkFVj99KOmM1xV5@fish.rmq.cloudamqp.com/covprbbm";
                    amqpuri           = "amqp://covprbbm:9exUT3KY19XpOfngiwkFVj99KOmM1xV5@184.72.215.102/covprbbm";
                    //clientProvideName ="androidSimu";
                    //
                    int pid           = android.os.Process.myPid();
                    int taskId        = getTaskId();
                    //clientProvideName = clientProvideName + "_" + String.format("%d_%d",pid,taskId);
                    //clientProvideName = clientProvideName + "_" + String.format("%d",taskId);
                    //
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");// HH:mm:ss//获取当前时间
                    Date date = new Date(System.currentTimeMillis());
                    //clientProvideName = clientProvideName + "_" + simpleDateFormat.format(date);
                    //
                    factory.setUri(new URI(amqpuri));
                    //
                    factory.setAutomaticRecoveryEnabled(true);
                    factory.setTopologyRecoveryEnabled(true);
                    //
                    connection = factory.newConnection(clientProvideName);
                    if (connection==null)
                    {
                        onMessageReceived("[disconnected]:"+clientProvideName);
                        return;
                    }
                    //
                    connection.addShutdownListener(new ShutdownListener() {
                        @Override
                        public void shutdownCompleted(ShutdownSignalException e) {
                            String connectionOrChannel = e.isHardError() ? "connection" : "channel";
                            String appInitiated        = "clean " + connectionOrChannel + " shutdown";
                            String nonAppInitiated     = connectionOrChannel + " error";
                            String explanation         = e.isInitiatedByApplication() ? appInitiated : nonAppInitiated;
                            send_notification("shutdownCompleted",e.getReason().protocolMethodName()+":"+explanation);
                            if (e.isHardError())
                            {
                                Connection conn = (Connection)e.getReference();
                                if (!e.isInitiatedByApplication())
                                {

                                }
                            }
                            else {
                                Channel ch = (Channel)e.getReference();
                            }
                        }
                    });
                    //
                    onMessageReceived("[connected]:"+clientProvideName);
                    readChannel = connection.createChannel();
                    publishChannel = connection.createChannel();
                    if (readChannel==null)
                    {
                        onMessageReceived("[disconnectedChannel]:"+clientProvideName);
                        return;
                    }
                    //Log.d("twapui", "1.testui");
                    readChannel.queueBind(Constants.QUEUE_NAME, Constants.EXCHANGE_NAME, Constants.EXCHANGE_DEFAULT_ROUTE);
                    Consumer consumer = new DefaultConsumer(readChannel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope,
                                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
                            //onMessageReceived(new String(body, "UTF-8"));
                            Object mailSubject = properties.getHeaders().get(Constants.MAIL_SUBJECT);
                            Object mailBody    = properties.getHeaders().get(Constants.MAIL_BODY);
                            Object mailTag     = properties.getHeaders().get(Constants.MAIL_TAG);
                            Object objNotifyId = properties.getHeaders().get(Constants.REQUEST_NOTIFICATION_ID);
                            Log.d("twapui", "handleDelivery:mailSubject:"+new String(mailSubject.toString()));
                            Log.d("twapui", "handleDelivery:mailBody:"+new String(mailBody.toString()));
                            //
                            String tag = "N/A";
                            if ( mailTag!=null)
                            {
                                tag = mailTag.toString();
                            }
                            String Subject = mailSubject.toString();
                            String Body    = mailBody.toString();
                            onMessageReceived(clientProvideName+":" + tag + "\r\n"+Subject,Body);
                            Log.d("twapui", "handleDelivery:mailTag:"+tag+":filter:"+filter);
                            //
                            boolean bFilter = true;
                            if (filter!=""&&filter!=null)
                            {
                                boolean bMatch = Pattern.matches(filter, tag) || Pattern.matches(filter, mailSubject.toString())|| Pattern.matches(filter, mailBody.toString());
                                //send_reply_broadcast_notification(mailSubject.toString(), mailBody.toString(),String.format("%s:%s:filter:%s:Pattern.matches",clientProvideName,String.valueOf(bMatch),filter));
                                if (tag.indexOf(filter)>=0||mailBody.toString().indexOf(filter)>=0||mailSubject.toString().indexOf(filter)>=0)
                                {
                                    bMatch = true;
                                }
                                //send_reply_broadcast_notification(mailSubject.toString(), mailBody.toString(),String.format("%s:%s:filter:%s:indexOf",clientProvideName,String.valueOf(bMatch),filter));
                                Log.d("twapui", String.format("handleDelivery.Filter.Result:%s:%s:%s",String.valueOf(bMatch),filter,tag));
                                bFilter = bMatch;
                            }
                            //
                            if (bFilter==true) {
                                int requestNotifyId = 0;
                                if (objNotifyId != null) {
                                    requestNotifyId = Integer.parseInt(objNotifyId.toString());
                                    send_reply_broadcast_notification(Subject,Body,clientProvideName+":"+tag, requestNotifyId);
                                    Log.d("twapui", String.format("handleDelivery.requestNotifyId:%d", requestNotifyId));
                                } else {
                                    send_reply_broadcast_notification(Subject, Body, tag);
                                }
                            }
                            //
                            //Log.d("twapui", "3.testui");
                        }
                    };
                    readChannel.basicConsume(Constants.QUEUE_NAME, true, consumer);
                } catch (Exception e) {
                    e.getMessage();
                    e.printStackTrace();
                }
            }
        });
        connectionThread.start();

        //Log.d("twapui", "2.testui");
    }
    //
}

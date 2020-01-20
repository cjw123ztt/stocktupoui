package com.example.stocktupoui;

import android.net.Uri;
//
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import androidx.core.app.RemoteInput;
import android.os.Bundle;
//
import android.util.Log;
//
public class NotificationBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("twapui", action);
        if (action.equals("com.twapui.stock.click")) {
            //处理点击事件
            System.out.println("click");
            //email intent
            Intent emailIntent=new Intent();
            emailIntent.setAction(Intent.ACTION_SEND );
            String[] tos={"18507558@qq.com"};
            String[] ccs={"18507558@qq.com"};
            emailIntent.putExtra(Intent.EXTRA_EMAIL, tos);
            emailIntent.putExtra(Intent.EXTRA_CC, ccs);
            if (intent.getExtras()!=null) {
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, intent.getExtras().getString(Constants.MAIL_TAG));
                emailIntent.putExtra(Intent.EXTRA_TEXT, intent.getExtras().getString(Constants.MAIL_BODY)+"\r\n"+intent.getExtras().getString(Constants.MAIL_SUBJECT));
            }
            emailIntent.setType("message/rfc822");
            //
            Intent outputIntent = Intent.createChooser(emailIntent, "Choose Email ...");
            if (outputIntent!=null) {
                outputIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(outputIntent);
            }
            //ie intent
            Uri uri = Uri.parse(intent.getExtras().getString(Constants.MAIL_BODY)); //浏览器
            if (uri!=null)
            {
                Intent ieIntent = new Intent();
                ieIntent.setAction(Intent.ACTION_VIEW);
                ieIntent.setData(uri);
                ieIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ieIntent);
            }
        }
        else if (action.equals("com.xxx.xxx.cancel")) {
            //处理滑动清除和点击删除事件
            System.out.println("cancel");
        }
        else if (action.equals("com.twapui.stock.reply")) {
            System.out.println("reply");
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            String message = null;
            if (remoteInput != null) {
                message = remoteInput.getCharSequence(Constants.KEY_TEXT_REPLY).toString();
                Log.d("twapui", message);
            }
        }
    }
}
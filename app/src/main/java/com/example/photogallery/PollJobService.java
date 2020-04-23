package com.example.photogallery;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PollJobService extends JobService {
    private static final String TAG = "PollJobService";
    private PollTask mCurrentTask;
    private static final int JOB_ID = 1;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "onStartJob: ------>");
        new PollTask().execute(params);
        /*mCurrentTask = new PollTask();
        mCurrentTask.execute(params);*/
        return true; //при делегации потоков
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "onStopJob: ------>");
        /*if (mCurrentTask != null){
            mCurrentTask.cancel(true);
        }*/
        return false; // не становится вновь на очередь
    }

    public static void setServiceAlarm(Context context, boolean isOn) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (isOn) {
            Log.i(TAG, "JobAlarm set");
            JobInfo jobInfo = new JobInfo.Builder(
                    JOB_ID, new ComponentName(context, PollJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    //.setPeriodic(PollService.POLL_INTERVAL_MS)
                    .setPeriodic(1000*60*1)
                    .setPersisted(true)
                    .build();

            int result = scheduler.schedule(jobInfo);
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Задача успешно запланирована!");
            }
        } else {
            scheduler.cancel(JOB_ID);
        }
    }

    public static boolean isServiceAlarmOn(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        boolean hasbeenSchedule = false;
        assert scheduler != null;
        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == JOB_ID) {
                hasbeenSchedule = true;
                break;
            }
        }
        return hasbeenSchedule;
    }

    private class PollTask extends AsyncTask<JobParameters, Void, JobParameters>{

        @Override
        protected JobParameters doInBackground(JobParameters... jobParameters) {
            JobParameters jobParams = jobParameters[0];

            // проверка новых изображений на Flickr
            try {
                Log.i(TAG, "checking new images in async");
                PollServiceGeneral.pollImages(PollJobService.this);
                jobFinished(jobParams, true);
            } catch (Exception ex) {
                jobFinished(jobParams, false);
            }
            return jobParameters[0];
        }

    }
}



package com.example.android.uamp.model;

/**
 * Created by Kyler C on 11/2/2017.
 */

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import java.util.Iterator;
import java.util.List;

/**
 * Example stub job to monitor when there is a change to photos in the media provider.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DBJob extends JobService {
    // The root URI of the media provider, to monitor for generic changes to its content.
    static final Uri MEDIA_URI = Uri.parse("/storage/");

    // A pre-built JobInfo we use for scheduling our job.
    static final JobInfo JOB_INFO;

    static int jobID = 626262;

    private DBBuilder DBB;
    private MusicProviderSource mSource;

    public DBJob(DBBuilder DBB) {
        this.DBB = DBB;
    }

    static {
        JobInfo.Builder builder = new JobInfo.Builder(jobID,
                new ComponentName("com.example.android.apis", DBJob.class.getName()));
        // Look for general reports of changes in the overall provider.
        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MEDIA_URI, 0));
        JOB_INFO = builder.build();
    }

    // Fake job work.  A real implementation would do some work on a separate thread.
    final Handler mHandler = new Handler();
    final Runnable mWorker = new Runnable() {
        @Override public void run() {
            scheduleJob(DBJob.this);
            jobFinished(mRunningParams, false);
        }
    };

    JobParameters mRunningParams;

    // Schedule this job, replace any existing one.
    public static void scheduleJob(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        js.schedule(JOB_INFO);
        Log.i("DBJob", "JOB SCHEDULED!");
    }

    // Check whether this job is currently scheduled.
    public static boolean isScheduled(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        List<JobInfo> jobs = js.getAllPendingJobs();
        if (jobs == null) {
            return false;
        }
        for (int i=0; i<jobs.size(); i++) {
            if (jobs.get(i).getId() == jobID) {
                return true;
            }
        }
        return false;
    }

    // Cancel this job, if currently scheduled.
    public static void cancelJob(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        js.cancel(jobID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i("DBJob", "JOB STARTED!");

        updateMedia();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mHandler.removeCallbacks(mWorker);
        return false;
    }

    private synchronized void updateMedia() {
        //mSource = new MusicFileSource(DBB, cc);

        Iterator<MediaMetadataCompat> tracks = mSource.iterator();
        while (tracks.hasNext()) {
            MediaMetadataCompat item = tracks.next();
            //DBB.insertFromMetadata(item);
            //DBB.printColumn("Title");
        }
    }

}
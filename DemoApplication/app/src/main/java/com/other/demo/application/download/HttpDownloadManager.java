package com.other.demo.application.download;

import android.support.annotation.NonNull;
import android.util.Log;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;




public class HttpDownloadManager {

    private static final String TAG = "HttpDownloadManager";
    private String apkUrl, apkName, downloadPath;
    private OnDownloadListener listener;

    public HttpDownloadManager(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public void download(String apkUrl, String apkName, OnDownloadListener listener) {
        this.apkUrl = apkUrl;
        this.apkName = apkName;
        this.listener = listener;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(Constant.THREAD_NAME);
                return thread;
            }
        });
        executor.execute(runnable);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (FileUtil.fileExists(downloadPath, apkName)) {
                FileUtil.delete(downloadPath, apkName);
            }
            fullDownload();
        }
    };

    /**
     * 全部下载
     */
    private void fullDownload() {

        listener.start();
        try {
            URL url = new URL(apkUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setReadTimeout(Constant.HTTP_TIME_OUT);
            con.setConnectTimeout(Constant.HTTP_TIME_OUT);
            con.setRequestProperty("Accept-Encoding", "identity");
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = con.getInputStream();
                int length = con.getContentLength();
                int len;
                //当前已下载完成的进度
                int progress = 0;
                byte[] buffer = new byte[1024 * 2];
                File file = FileUtil.createFile(downloadPath, apkName);
                FileOutputStream stream = new FileOutputStream(file);
                while ((len = is.read(buffer)) != -1) {
                    //将获取到的流写入文件中
                    stream.write(buffer, 0, len);
                    progress += len;
                    listener.downloading(length, progress);
                }
                listener.done(file);
                //完成io操作,释放资源
                stream.flush();
                stream.close();
                is.close();
                //重定向
            } else if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
                    con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                apkUrl = con.getHeaderField("Location");
                con.disconnect();
                Log.e(TAG, "fullDownload: 当前地址是重定向Url，定向后的地址：" + apkUrl);
                fullDownload();
            } else {
                listener.error(new SocketTimeoutException("连接超时！"));
            }
            con.disconnect();
        } catch (Exception e) {
            listener.error(e);
            e.printStackTrace();
        }
    }
}

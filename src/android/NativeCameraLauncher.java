/*
	    Copyright 2014 Giovanni Di Gregorio.

		Licensed under the Apache License, Version 2.0 (the "License");
		you may not use this file except in compliance with the License.
		You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

		Unless required by applicable law or agreed to in writing, software
		distributed under the License is distributed on an "AS IS" BASIS,
		WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		See the License for the specific language governing permissions and
   		limitations under the License.
 */

package com.tmantman.nativecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;

import com.tmantman.nativecamera.ExifHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

/**
 * This class launches the camera view, allows the user to take a picture,
 * closes the camera view, and returns the captured image. When the camera view
 * is closed, the screen displayed before the camera view was shown is
 * redisplayed.
 */
public class NativeCameraLauncher extends CordovaPlugin {

	private static final String LOG_TAG = "NativeCameraLauncher";

	private int mQuality;
	private int targetWidth;
	private int targetHeight;
	private Uri imageUri;
	private File photo;
	private static final String _DATA = "_data";
	private CallbackContext callbackContext;
	private String date = null;

	public NativeCameraLauncher() {
	}

	void failPicture(String reason) {
		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, reason));
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";
		this.callbackContext = callbackContext;
		try {
			if (action.equals("takePicture")) {
				this.targetHeight = 0;
				this.targetWidth = 0;
				this.mQuality = 80;
				this.targetHeight = args.getInt(4);
				this.targetWidth = args.getInt(3);
				this.mQuality = args.getInt(0);
				this.takePicture();
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				callbackContext.sendPluginResult(r);
				return true;
			}
			return false;
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
			return true;
		}
	}

	public void takePicture() {
		// Save the number of images currently on disk for later
		Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), CameraActivity.class);
		this.photo = createCaptureFile();
		this.imageUri = Uri.fromFile(photo);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, this.imageUri);
		this.cordova.startActivityForResult((CordovaPlugin) this, intent, 1);
	}

	private File createCaptureFile() {
		File oldFile = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		if(oldFile.exists())
			oldFile.delete();
		Calendar c = Calendar.getInstance();
	    this.date = "" + c.get(Calendar.DAY_OF_MONTH)
					+ c.get(Calendar.MONTH)
					+ c.get(Calendar.YEAR)
					+ c.get(Calendar.HOUR_OF_DAY)
					+ c.get(Calendar.MINUTE)
					+ c.get(Calendar.SECOND);
		File photo = new File(getTempDirectoryPath(this.cordova.getActivity().getApplicationContext()), "Pic-" + this.date + ".jpg");
		return photo;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// If image available
		if (resultCode == Activity.RESULT_OK) {
			int rotate = 0;
			try {
				// Check if the image was written to
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				Bitmap bitmap = BitmapFactory.decodeFile(this.imageUri.getPath(), options);
				if (options.outWidth == -1 || options.outHeight == -1) {
					this.failPicture("Error decoding image.");
					return;
				}
				
				ExifHelper exif = new ExifHelper();
				exif.createInFile(this.imageUri.getPath());
				exif.readExifData();
				rotate = exif.getOrientation();
				Log.i(LOG_TAG, "Uncompressed image rotation value: " + rotate);

				exif.resetOrientation();
				exif.createOutFile(this.imageUri.getPath());
				exif.writeExifData();

				JSONObject returnObject = new JSONObject();
				returnObject.put("url", this.imageUri.toString());
				returnObject.put("rotation", rotate);

				Log.i(LOG_TAG, "Return data: " + returnObject.toString());

				PluginResult result = new PluginResult(PluginResult.Status.OK, returnObject);

				// Log.i(LOG_TAG, "Final Exif orientation value: " + exif.getOrientation());

				// Send Uri back to JavaScript for viewing image
				this.callbackContext.sendPluginResult(result);

			} catch (IOException e) {
				e.printStackTrace();
				this.failPicture("Error capturing image.");
			} catch (JSONException e) {
				e.printStackTrace();
				this.failPicture("Error capturing image.");
			}
		}

		// If cancelled
		else if (resultCode == Activity.RESULT_CANCELED) {
			this.failPicture("Camera cancelled.");
		}

		// If something else
		else {
			this.failPicture("Did not complete!");
		}
	}

	private String getTempDirectoryPath(Context ctx) {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/"
					+ ctx.getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = ctx.getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		if (!cache.exists()) {
			cache.mkdirs();
		}

		return cache.getAbsolutePath();
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
		this.callbackContext = callbackContext;

		this.mQuality = state.getInt("mQuality");
		this.targetWidth = state.getInt("targetWidth");
		this.targetHeight = state.getInt("targetHeight");

		this.imageUri = state.getParcelable("imageUri");
		this.photo = (File) state.getSerializable("photo");

		this.date = state.getString("date");

		super.onRestoreStateForActivityResult(state, callbackContext);
	}

	@Override
	public Bundle onSaveInstanceState() {

		Bundle state = new Bundle();
		state.putInt("mQuality", mQuality);
		state.putInt("targetWidth", targetWidth);
		state.putInt("targetHeight", targetHeight);
		state.putString("date", date);
		state.putParcelable("imageUri", imageUri);
		state.putSerializable("photo", photo);

		return state;
	}
}

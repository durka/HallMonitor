package org.durka.hallmonitor;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class NotificationAdapter extends BaseAdapter {
	
	private StatusBarNotification[] notifs;
	private Context that;

    private final int numOfItems = 10;
	
	public NotificationAdapter(Context ctx, StatusBarNotification[] n) {
		that = ctx;
		notifs = n;
	}
	
	public void update(StatusBarNotification[] n) {
		notifs = n;
		Log.d("NA.upd", "update: " + Integer.toString(n.length) + " notifications");
	}

	@Override
	public int getCount() {
		return (notifs.length < numOfItems ? numOfItems  + (notifs.length % 2) : notifs.length);
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convert, ViewGroup parent) {
		ImageView view;
		if (convert != null) {
			view = (ImageView)convert;
		} else {
			view = new ImageView(that);
			//view.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.MATCH_PARENT, GridView.LayoutParams.MATCH_PARENT));
            //view.setLayoutParams(new GridView.LayoutParams(GridView.LayoutParams.WRAP_CONTENT, GridView.LayoutParams.WRAP_CONTENT));
            view.setLayoutParams(new GridView.LayoutParams(30, 30));
			view.setScaleType(ImageView.ScaleType.FIT_CENTER);
			view.setPadding(0, 0, 0, 0);
			try {
                if (notifs.length > 0) {
                    int offset = Math.round((getCount() - notifs.length) / 2);
                    if (position >= offset && position < offset + notifs.length) {
                        StatusBarNotification sBN = notifs[position - offset];
				        view.setImageDrawable(that.createPackageContext(sBN.getPackageName(), 0).getResources().getDrawable(sBN.getNotification().icon));
                    }
                }
			} catch (NotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return view;
	}	    		


}

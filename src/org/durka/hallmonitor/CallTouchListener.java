package org.durka.hallmonitor;

import android.content.ClipData;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnTouchListener;

public class CallTouchListener implements OnTouchListener {

	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
	      if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
	    	  switch(view.getId()){
	    	  case R.id.pickup_button:
	    		  ClipData data = ClipData.newPlainText("Pickup", "Call");
	    		  DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
	  	          view.startDrag(data, shadowBuilder, view, 0);
	  	          view.setVisibility(View.INVISIBLE);
	    	  break;
	    	  case R.id.hangup_button:
	    		  ClipData data2 = ClipData.newPlainText("Hang", "Call");
	    		  DragShadowBuilder shadowBuilder2 = new View.DragShadowBuilder(view);
	  	          view.startDrag(data2, shadowBuilder2, view, 0);
	  	          view.setVisibility(View.INVISIBLE);
	    	  }
	        return true;
	      } else {
	        return false;
	      }
	}
}

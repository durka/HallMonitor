package org.durka.hallmonitor;

import android.util.Log;
import android.view.DragEvent;
import android.view.View;

public class CallDragListener implements View.OnDragListener {
	
    public boolean onDrag(View v, DragEvent dragevent) {

    	View dragView = (View) dragevent.getLocalState();
    	
      switch (dragevent.getAction()) {
      case DragEvent.ACTION_DRAG_STARTED:
    	  Log.d("DragnDrop", "Event received");
        break;
      case DragEvent.ACTION_DRAG_ENTERED:
    	  Log.d("DragnDrop", "Icon now is in target area");        
    	  break;
      case DragEvent.ACTION_DRAG_EXITED:
    	  Log.d("DragnDrop", "Icon now is out of the target area");
        break;
      case DragEvent.ACTION_DROP: {
    	  if (dragevent.getClipDescription().getLabel().equals("Pickup")) {
    		  Functions.Actions.pickup_call();
    		  dragView.setVisibility(View.VISIBLE);
    		  Log.d("DragnDrop", "PickUp Call");
    	  } if (dragevent.getClipDescription().getLabel().equals("Hang")) {
    		  Functions.Actions.hangup_call();
    		  dragView.setVisibility(View.VISIBLE);
    		  Log.d("DragnDrop", "Hangup Call");
    	  }
      }
    	  Log.d("DragnDrop", "Icon dropped in target area");
        break;
      case DragEvent.ACTION_DRAG_ENDED:
    	  if (dropEventNotHandled(dragevent)) {
    			  dragView.setVisibility(View.VISIBLE);
    			  Log.d("DragnDrop", "Not dropped in target area, restoring default");
          }
        break;
      }
      return true;
    }

    private boolean dropEventNotHandled(DragEvent event) {
        return !event.getResult();
    }

}

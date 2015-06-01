package se.kth.swim.msg;

import se.kth.swim.msg.net.NodeStatus;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class LocalPiggyBackElement extends PiggyBackElement{
	private int diseminateTimes;
	
	public LocalPiggyBackElement(NatedAddress address, NodeStatus status, int count,int diseminateTimes) {
		super();
		this.diseminateTimes=diseminateTimes;
		
	}

	public int getDiseminateTimes() {
		return diseminateTimes;
	}

	public void setDiseminateTimes(int diseminateTimes) {
		this.diseminateTimes = diseminateTimes;
	}
	
	public void dicreaseDisseminateTimes(){
		this.diseminateTimes--;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Local:"+this.getDiseminateTimes();
	}
	
	
}

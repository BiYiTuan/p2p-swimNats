package se.kth.swim.msg;

import se.kth.swim.msg.net.NodeStatus;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PiggyBackElement {
	private NatedAddress address;
	private NodeStatus status;
	private int count;
	private int diseminateTimes;

	public int getDiseminateTimes() {
		return diseminateTimes;
	}

	public void setDiseminateTimes(int diseminateTimes) {
		this.diseminateTimes = diseminateTimes;
	}
	
	public void dicreaseDisseminateTimes(){
		this.diseminateTimes--;
	}

	public PiggyBackElement() {

	}

	public PiggyBackElement(NatedAddress address, NodeStatus status, int count,int disseminateTimes) {
		this.address = address;
		this.status = status;
		this.count = count;
		this.diseminateTimes=disseminateTimes;
	}

	public NatedAddress getAddress() {
		return address;
	}

	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		final int prime=31;
		int result=1;
		result = prime*result*this.address.getId();
		//result = prime*result*this.getCount();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		if (obj.getClass()!=this.getClass()||obj==null){
			return false;
		}
		if (obj == this){
			return true;
		}
		PiggyBackElement element = (PiggyBackElement) obj;
		return (this.getAddress().getId()==element.getAddress().getId());
				//&&(this.getCount()==element.getCount()));
	}

	public void setAddress(NatedAddress address) {
		this.address = address;
	}

	public NodeStatus getStatus() {
		return status;
	}

	public void setStatus(NodeStatus status) {
		this.status = status;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}
	
	public void incrementCounter(){
		this.count++;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "Piggybacked element: "+this.address.toString() + ","+this.getStatus()+","+","+this.getCount()+","+this.getDiseminateTimes();
	}
	

}



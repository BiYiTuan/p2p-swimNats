package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PingReq extends FDMessage{
	
	private NatedAddress nodeToBePinged;

	public PingReq() {
		super();
		// TODO Auto-generated constructor stub
	}

	public PingReq(UUID sn, Map<Integer, PiggyBackElement> nodes,NatedAddress nodeToBePinged) {
		super(sn, nodes);
		this.nodeToBePinged=nodeToBePinged;
		// TODO Auto-generated constructor stub
	}

	public NatedAddress getNodeToBePinged() {
		return nodeToBePinged;
	}

	public void setNodeToBePinged(NatedAddress nodeToBePinged) {
		this.nodeToBePinged = nodeToBePinged;
	}
	
	

}

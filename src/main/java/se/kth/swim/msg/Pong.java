package se.kth.swim.msg;

import java.util.Map;
import java.util.UUID;

import se.kth.swim.msg.net.NodeStatus;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong extends FDMessage {


	public Pong(UUID sn, Map<Integer, PiggyBackElement> nodes) {
		super(sn, nodes);
		// TODO Auto-generated constructor stub
	}

	public Pong() {

	}

}

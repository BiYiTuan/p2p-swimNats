package se.kth.swim.msg.net;

import java.util.Map;
import java.util.UUID;

import se.kth.swim.msg.FDMessage;
import se.kth.swim.msg.PiggyBackElement;
import se.kth.swim.msg.Ping;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class IndirectPing extends Ping{

	public IndirectPing() {
		super();
		// TODO Auto-generated constructor stub
	}

	public IndirectPing(UUID sn, Map<Integer, PiggyBackElement> nodes,
			NatedAddress forwardNode, UUID initialUUID) {
		super(sn, nodes, forwardNode, initialUUID);
		// TODO Auto-generated constructor stub
	}

	

}

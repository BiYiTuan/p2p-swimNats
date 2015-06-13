package se.kth.swim.msg.net;

import java.util.Map;
import java.util.UUID;

import se.kth.swim.msg.FDMessage;
import se.kth.swim.msg.PiggyBackElement;

public class NatPong extends FDMessage{

	public NatPong() {
		super();
		// TODO Auto-generated constructor stub
	}

	public NatPong(UUID sn, Map<Integer, PiggyBackElement> nodes) {
		super(sn, nodes);
		// TODO Auto-generated constructor stub
	}

}

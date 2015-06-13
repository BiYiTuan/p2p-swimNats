package se.kth.swim.msg.net;

import se.sics.kompics.PortType;

public class NatPort extends PortType{

	{
		indication(NetNatRequest.class);
		indication(NetNatUpdate.class);
	    request(NetNatResponse.class);	
	}
	
}

package se.kth.swim.msg.net;

import se.kth.swim.msg.FDMessage;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatPing extends NetMsg<NatPing>{

	public NetNatPing(NatedAddress src, NatedAddress dst, NatPing content) {
		super(src, dst, content);
		// TODO Auto-generated constructor stub
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		// TODO Auto-generated method stub
		return null;
	}

}

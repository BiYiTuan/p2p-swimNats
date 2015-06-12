package se.kth.swim.msg.net;

import se.kth.swim.msg.Ping;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetIndirectPing extends NetMsg<IndirectPing>{


	public NetIndirectPing(NatedAddress src, NatedAddress dst, IndirectPing content) {
		super(src, dst, content);
		// TODO Auto-generated constructor stub
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		// TODO Auto-generated method stub
		return null;
	}

}

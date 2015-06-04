package se.kth.swim.msg.net;

import se.kth.swim.msg.PingReq;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPingReq extends NetMsg<PingReq>{

	public NetPingReq(NatedAddress src, NatedAddress dst, PingReq content) {
		super(src, dst, content);
		// TODO Auto-generated constructor stub
	}

	public NetPingReq(Header<NatedAddress> newHeader, PingReq content) {
		// TODO Auto-generated constructor stub
		super(newHeader,content);
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		// TODO Auto-generated method stub
		return new NetPingReq(newHeader,getContent());
	}

}

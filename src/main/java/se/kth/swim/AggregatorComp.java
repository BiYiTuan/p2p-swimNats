/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    //connects to p2p simulator
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Map<Integer,Status> nodes;

    private final NatedAddress selfAddress;
    
    private boolean converged;
    //time in milliseconds
    private long init;
    private int first;
    private UUID timerID;
    private long time;
    
    private List<Integer> killedNodes;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        this.killedNodes=new ArrayList<Integer>();
        this.converged=true;
        this.nodes = new HashMap<Integer,Status>();
        this.init=0;
        this.first=0;
        log.info("{} initiating....", new Object[]{selfAddress.getId()});

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
        subscribe(handleEvaluationTimer,timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
            if (timerID!=null){
            	cancelTimer(timerID);
            }
        }

    };

    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
        	if (converged){
            log.info("{} status from:{} pings:{} , alive : {} suspected : {} failed : {}", 
                    new Object[]{selfAddress.getId(), status.getHeader().getSource(), status.getContent().receivedPings,status.getContent().getAliveNodes(),status.getContent().getSuspectedNodes(),status.getContent().getFailedNodes()});
        	}
        	
        	if (first==0 && killedNodes.size()>0 && status.getContent().getFailedNodes()>0){
        		first++;
        		time=init;
        	}
        }
        	
    };
    
    private Handler<EvaluationTimer> handleEvaluationTimer = new Handler<EvaluationTimer>(){

		@Override
		public void handle(EvaluationTimer event) {
			// TODO Auto-generated method stub
			time++;
		}
    	
    };
    
    private UUID scheduleTimer() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(0, 100);
        EvaluationTimer at = new EvaluationTimer(spt);
        spt.setTimeoutEvent(at);
        timerID = at.getTimeoutId();
        trigger(spt, timer);

        return timerID;
      }

      private void cancelTimer(UUID timerID) {
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(timerID);
        trigger(cpt, timer);
        timerID = null;
      }

    public static class AggregatorInit extends Init<AggregatorComp> {

        public final NatedAddress selfAddress;

        public AggregatorInit(NatedAddress selfAddress) {
            this.selfAddress = selfAddress;
        }
    }
    
    private static class  EvaluationTimer extends Timeout{

		protected EvaluationTimer(SchedulePeriodicTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    	
    }
}

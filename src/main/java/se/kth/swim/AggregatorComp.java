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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.NodeStatus;
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
    
    //start calculation
    private long start;
    
    private boolean startT;
    //end calculation
    private long end;
    
    private final Integer NETWORK;
    
    private Integer first;
    private List<Integer> willBeKilled;
    
    private long evaluationTimer;
    private boolean convergedFailure;
    private boolean convergedRecovery;
    private UUID id;
   

    private Map<Integer,Status> globalView= new HashMap<Integer,Status>();
    private final NatedAddress selfAddress;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating....", new Object[]{selfAddress.getId()});
        this.NETWORK=init.size;
        this.willBeKilled=init.killed;
        this.start=0;
        this.end=0;
        this.convergedFailure=true;
        this.convergedRecovery=true;
        this.evaluationTimer=0;
        this.startT=true;
        this.first=0;
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
        subscribe(handleEvaluationTimer,timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
            scheduleEvaluationTimer();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
            if (id!=null){
            	cancelEvaluationTimer(id);
            }
        }

    };

    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
			log.info(
					"{} status from:{} pings:{} , alive : {} suspected : {} failed : {}",
					new Object[] { selfAddress.getId(),
							status.getHeader().getSource(),
							status.getContent().receivedPings,
							status.getContent().getAliveNodes(),
							status.getContent().getSuspectedNodes(),
							status.getContent().getFailedNodes() });

			startFailureEvaluation(status);
        }
    };
    
    private void startFailureEvaluation(NetStatus status){
		if ((first.equals(0)) && (status.getContent().getFailedNodes() > 0)
				&& (willBeKilled.size() > 0)) {
			log.info("aggregator starts counting");
			start = evaluationTimer;
			first++;
		} else if (startT) {
			startT=false;
			start = evaluationTimer;
		}

		if (globalView.containsKey(status.getSource().getId())
				&& willBeKilled.contains(status.getSource().getId())) {
			if (status.getContent().getAliveNodes()
					.equals(NETWORK - willBeKilled.size())
					&& status.getContent().getFailedNodes()
							.equals(willBeKilled.size())) {
				globalView.put(status.getSource().getId(),
						status.getContent());
			} else {
				globalView.remove(status.getSource().getId());
			}
		} else {
			if (status.getContent().getAliveNodes()
					.equals(NETWORK - willBeKilled.size())
					&& status.getContent().getFailedNodes()
							.equals(willBeKilled.size())) {
				globalView.put(status.getSource().getId(),
						status.getContent());
			}
		}
		if (globalView.size() == (NETWORK - willBeKilled.size())
		        && convergedFailure) {
			convergedFailure = false;

		        log.info("System converged in {} ms ", evaluationTimer
		          - start);
		      }
    }
    
    private Handler<EvaluationTimer> handleEvaluationTimer = new Handler<EvaluationTimer>(){

		@Override
		public void handle(EvaluationTimer event) {
			// TODO Auto-generated method stub
			evaluationTimer++;
		}
    	
    };
    
    private UUID scheduleEvaluationTimer() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(0, 100);
        EvaluationTimer et = new EvaluationTimer(spt);
        spt.setTimeoutEvent(et);
        id = et.getTimeoutId();
        trigger(spt, timer);
        return id;
      }

      private void cancelEvaluationTimer(UUID timerID) {
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(timerID);
        trigger(cpt, timer);
        id = null;
      }

    public static class AggregatorInit extends Init<AggregatorComp> {

        public final NatedAddress selfAddress;
        public final int size;
        public final List<Integer> killed;

        public AggregatorInit(NatedAddress selfAddress,int size,List<Integer> killed) {
            this.selfAddress = selfAddress;
            this.size=size;
            this.killed=killed;
        }
    }
    
    private static class EvaluationTimer extends Timeout{

		protected EvaluationTimer(SchedulePeriodicTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    	
    }
}

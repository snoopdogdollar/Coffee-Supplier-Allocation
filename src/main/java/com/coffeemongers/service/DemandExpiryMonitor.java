package com.coffeemongers.service;

import com.coffeeallocation.service.DemandExpiryService;
import java.util.concurrent.*;

/** Desktop lifecycle maintenance; no connection is opened in Demo or signed-out sessions. */
public final class DemandExpiryMonitor implements AutoCloseable {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("demand-expiry").factory());
    public void start(){worker.scheduleWithFixedDelay(() -> {
        try{
            if(AppServices.liveAdminOverview().isPresent()||AppServices.liveBuyerDemands().isPresent()||AppServices.liveSupplierSupply().isPresent())new DemandExpiryService().expireOverdue();
        }catch(Exception exception){System.getLogger(DemandExpiryMonitor.class.getName()).log(System.Logger.Level.WARNING,"Could not update expired demands; automatic maintenance will retry.");}
    },0,60,TimeUnit.SECONDS);}
    @Override public void close(){worker.shutdownNow();}
}

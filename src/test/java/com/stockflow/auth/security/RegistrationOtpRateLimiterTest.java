package com.stockflow.auth.security;

import static org.assertj.core.api.Assertions.*;
import com.stockflow.common.exception.AppException;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegistrationOtpRateLimiterTest {
    @Test void windowExpiresAndVerifyResendBudgetsAreSeparate() {
        var clock=new MutableClock();var limiter=new RegistrationOtpRateLimiter(clock);
        for(int i=0;i<10;i++)limiter.checkVerify("203.0.113.1","a@private.test");
        assertThatThrownBy(()->limiter.checkVerify("203.0.113.1"," A@PRIVATE.TEST ")).isInstanceOf(AppException.class);
        for(int i=0;i<5;i++)limiter.checkResend("203.0.113.1","a@private.test");
        assertThatThrownBy(()->limiter.checkResend("203.0.113.1","a@private.test")).isInstanceOf(AppException.class);
        clock.now=60_000;limiter.checkVerify("203.0.113.1","a@private.test");limiter.checkResend("203.0.113.1","a@private.test");
    }
    @Test void simultaneousRequestsCannotRacePastIpLimit() throws Exception {
        var limiter=new RegistrationOtpRateLimiter(new MutableClock());var allowed=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);
        try{
            var tasks=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<40;i++){int n=i;tasks.add(pool.submit(()->{try{start.await();limiter.checkVerify("203.0.113.1",n+"@private.test");allowed.incrementAndGet();}catch(AppException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}));}
            start.countDown();for(var task:tasks)task.get(10,TimeUnit.SECONDS);assertThat(allowed.get()).isEqualTo(30);
        }finally{pool.shutdownNow();}
    }
    @Test void activeBucketLimitFailsClosedAndExpiredBucketsCanBeReclaimed() {
        var clock=new MutableClock();var limiter=new RegistrationOtpRateLimiter(clock);
        for(int i=0;i<4096;i++)limiter.checkVerify("ip-"+i,i+"@private.test");
        assertThatThrownBy(()->limiter.checkVerify("new-ip","new@private.test")).isInstanceOf(AppException.class);
        clock.now=60_000;limiter.checkVerify("new-ip","new@private.test");
    }
    private static class MutableClock extends Clock {
        long now;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return Instant.ofEpochMilli(now);}
        @Override public long millis(){return now;}
    }
}

/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.core.transaction;

import java.util.List;

import javax.transaction.xa.Xid;

import org.hornetq.api.core.HornetQException;

/**
 * A HornetQ internal transaction
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:andy.taylor@jboss.org>Andy Taylor</a>
 */
public interface Transaction
{
   void prepare() throws Exception;

   void commit() throws Exception;

   void commit(boolean onePhase) throws Exception;

   void rollback() throws Exception;

   /** Used for pages during commit.
    *  When paging messages we need to guarantee that they are in the same transaction (but not with the same set of TransactionOperation). */
   Transaction copy();

   int getOperationsCount();

   long getID();

   Xid getXid();

   void suspend();

   void resume();

   State getState();

   void setState(State state);

   void markAsRollbackOnly(HornetQException exception);

   long getCreateTime();

   void addOperation(TransactionOperation sync);

   public List<TransactionOperation> getAllOperations();

   boolean hasTimedOut(long currentTime, int defaultTimeout);

   boolean isWaitBeforeCommit();

   void setWaitBeforeCommit(boolean waitBeforeCommit);

   void putProperty(int index, Object property);

   Object getProperty(int index);

   void setContainsPersistent();

   boolean isContainsPersistent();

   void setTimeout(int timeout);

   // To be used by sub-contexts. Mainly on paging

   void beforeCommit() throws Exception;

   void beforeRollback() throws Exception;

   void beforePrepare() throws Exception;;

   void afterPrepare();

   void afterCommit();

   void afterRollback();

   static enum State
   {
      ACTIVE, PREPARED, COMMITTED, ROLLEDBACK, SUSPENDED, ROLLBACK_ONLY
   }

}

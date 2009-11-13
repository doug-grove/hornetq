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

package org.hornetq.tests.integration.client;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.client.impl.ClientSessionInternal;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.remoting.RemotingConnection;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.UnitTestCase;

/**
 * A SessionClosedOnRemotingConnectionFailureTest
 *
 * @author Tim Fox
 
 */
public class SessionClosedOnRemotingConnectionFailureTest extends UnitTestCase
{
   private HornetQServer server;

   private ClientSessionFactory sf;

   public void testSessionClosedOnRemotingConnectionFailure() throws Exception
   {
      ClientSession session = null;

      try
      {
         session = sf.createSession();

         session.createQueue("fooaddress", "fooqueue");

         ClientProducer prod = session.createProducer("fooaddress");

         ClientConsumer cons = session.createConsumer("fooqueue");

         session.start();

         prod.send(session.createClientMessage(false));

         assertNotNull(cons.receive());

         // Now fail the underlying connection

         RemotingConnection connection = ((ClientSessionInternal)session).getConnection();

         connection.fail(new HornetQException(HornetQException.NOT_CONNECTED));
         
         assertTrue(session.isClosed());
         
         assertTrue(prod.isClosed());
         
         assertTrue(cons.isClosed());
         
         //Now try and use the producer
         
         try
         {
            prod.send(session.createClientMessage(false));
            
            fail("Should throw exception");
         }
         catch (HornetQException e)
         {
            assertEquals(HornetQException.OBJECT_CLOSED, e.getCode());
         }
         
         try
         {
            cons.receive();
            
            fail("Should throw exception");
         }
         catch (HornetQException e)
         {
            assertEquals(HornetQException.OBJECT_CLOSED, e.getCode());            
         }        
         
         session.close();
      }
      finally
      {
         if (session != null)
         {
            session.close();
         }
      }
   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      Configuration config = new ConfigurationImpl();
      config.getAcceptorConfigurations().add(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()));
      config.setSecurityEnabled(false);
      server = HornetQ.newHornetQServer(config, false);

      server.start();

      sf = new ClientSessionFactoryImpl(new TransportConfiguration(InVMConnectorFactory.class.getName()));
   }

   @Override
   protected void tearDown() throws Exception
   {
      if (sf != null)
      {
         sf.close();
      }

      if (server != null)
      {
         server.stop();
      }

      sf = null;

      server = null;

      super.tearDown();
   }
}
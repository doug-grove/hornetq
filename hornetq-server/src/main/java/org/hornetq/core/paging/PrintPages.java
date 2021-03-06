/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.core.paging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.HornetQBuffers;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.journal.PreparedTransactionInfo;
import org.hornetq.core.journal.RecordInfo;
import org.hornetq.core.journal.SequentialFileFactory;
import org.hornetq.core.journal.impl.JournalImpl;
import org.hornetq.core.journal.impl.NIOSequentialFileFactory;
import org.hornetq.core.paging.cursor.PagePosition;
import org.hornetq.core.paging.cursor.impl.PagePositionImpl;
import org.hornetq.core.paging.impl.Page;
import org.hornetq.core.paging.impl.PageTransactionInfoImpl;
import org.hornetq.core.paging.impl.PagingManagerImpl;
import org.hornetq.core.paging.impl.PagingStoreFactoryNIO;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.persistence.impl.journal.JournalStorageManager;
import org.hornetq.core.persistence.impl.journal.JournalStorageManager.CursorAckRecordEncoding;
import org.hornetq.core.persistence.impl.journal.JournalStorageManager.PageUpdateTXEncoding;
import org.hornetq.core.persistence.impl.nullpm.NullStorageManager;
import org.hornetq.core.settings.HierarchicalRepository;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.core.settings.impl.HierarchicalObjectRepository;
import org.hornetq.utils.ExecutorFactory;
/**
 * A PrintPage
 *
 * @author clebertsuconic
 *
 *
 */
public class PrintPages // NO_UCD (unused code)
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public static void main(final String arg[])
   {
      if (arg.length != 2)
      {
         System.err.println("Usage: PrintPages <page foler> <journal folder>");
         System.exit(-1);
      }
      try
      {

         PageCursorsInfo cursorACKs = PrintPages.loadCursorACKs(arg[1]);

         Set<Long> pgTXs = cursorACKs.getPgTXs();

         ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1);
         final ExecutorService executor = Executors.newFixedThreadPool(10);
         ExecutorFactory execfactory = new ExecutorFactory()
         {

            public Executor getExecutor()
            {
               return executor;
            }
         };
         PagingStoreFactory pageStoreFactory = new PagingStoreFactoryNIO(arg[0], 1000l, scheduled, execfactory, false, null);
         HierarchicalRepository<AddressSettings> addressSettingsRepository = new HierarchicalObjectRepository<AddressSettings>();
         addressSettingsRepository.setDefault(new AddressSettings());
         StorageManager sm = new NullStorageManager();
         PagingManager manager = new PagingManagerImpl(pageStoreFactory, sm, addressSettingsRepository);

         manager.start();

         SimpleString stores[] = manager.getStoreNames();

         for (SimpleString store : stores)
         {
            PagingStore pgStore = manager.getPageStore(store);
            String folder = null;

            if (pgStore != null)
            {
               folder = pgStore.getFolder();
            }
            System.out.println("####################################################################################################");
            System.out.println("Exploring store " + store + " folder = " + folder);
            int pgid = (int)pgStore.getFirstPage();
            for (int pg = 0; pg < pgStore.getNumberOfPages(); pg++)
            {
               System.out.println("*******   Page " + pgid);
               Page page = pgStore.createPage(pgid);
               page.open();
               List<PagedMessage> msgs = page.read(sm);
               page.close();

               int msgID = 0;

               for (PagedMessage msg : msgs)
               {
                  msg.initMessage(sm);
                  System.out.print("pg=" + pgid + ", msg=" + msgID + ",pgTX=" + msg.getTransactionID() + ", msg=" + msg.getMessage());
                  System.out.print(",Queues = ");
                  long q[] = msg.getQueueIDs();
                  for (int i = 0; i < q.length; i++)
                  {
                     System.out.print(q[i]);

                     PagePosition posCheck = new PagePositionImpl(pgid, msgID);

                     boolean acked = false;

                     Set<PagePosition> positions = cursorACKs.getCursorRecords().get(q[i]);
                     if (positions != null)
                     {
                        acked = positions.contains(posCheck);
                     }

                     if (acked)
                     {
                        System.out.print(" (ACK)");
                     }

                     if (cursorACKs.getCompletePages(q[i]).contains(Long.valueOf(pgid)))
                     {
                        System.out.println(" (PG-COMPLETE)");
                     }


                     if (i + 1 < q.length)
                     {
                        System.out.print(",");
                     }
                  }
                  if (msg.getTransactionID() >= 0 && !pgTXs.contains(msg.getTransactionID()))
                  {
                     System.out.print(", **PG_TX_NOT_FOUND**");
                  }
                  System.out.println();
                  msgID++;
               }

               pgid++;

            }
         }

      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   private static class PageCursorsInfo
   {
      private final Map<Long, Set<PagePosition>> cursorRecords = new HashMap<Long, Set<PagePosition>>();

      private final Set<Long> pgTXs = new HashSet<Long>();

      private final  Map<Long, Set<Long>> completePages = new HashMap<Long, Set<Long>>();

      public PageCursorsInfo()
      {
      }


      /**
       * @return the pgTXs
       */
      public Set<Long> getPgTXs()
      {
         return pgTXs;
      }


      /**
       * @return the cursorRecords
       */
      public Map<Long, Set<PagePosition>> getCursorRecords()
      {
         return cursorRecords;
      }


      /**
       * @return the completePages
       */
      public  Map<Long, Set<Long>> getCompletePages()
      {
         return completePages;
      }

      public Set<Long> getCompletePages(Long queueID)
      {
         Set<Long> completePagesSet = completePages.get(queueID);

         if (completePagesSet == null)
         {
            completePagesSet = new HashSet<Long>();
            completePages.put(queueID, completePagesSet);
         }

         return completePagesSet;
      }

   }

   /**
    * @param journalLocation
    * @return
    * @throws Exception
    */
   protected static PageCursorsInfo loadCursorACKs(final String journalLocation) throws Exception
   {
      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(journalLocation, null);

      // Will use only default values. The load function should adapt to anything different
      ConfigurationImpl defaultValues = new ConfigurationImpl();

      JournalImpl messagesJournal = new JournalImpl(defaultValues.getJournalFileSize(),
         defaultValues.getJournalMinFiles(),
         0,
         0,
         messagesFF,
         "hornetq-data",
         "hq",
         1);

      messagesJournal.start();

      ArrayList<RecordInfo> records = new ArrayList<RecordInfo>();
      ArrayList<PreparedTransactionInfo> txs = new ArrayList<PreparedTransactionInfo>();

      messagesJournal.load(records, txs, null, false);

      PageCursorsInfo cursorInfo = new PageCursorsInfo();


      for (RecordInfo record : records)
      {
         byte[] data = record.data;

         HornetQBuffer buff = HornetQBuffers.wrappedBuffer(data);

         if (record.userRecordType == JournalStorageManager.ACKNOWLEDGE_CURSOR)
         {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
            encoding.decode(buff);

            Set<PagePosition> set = cursorInfo.getCursorRecords().get(encoding.queueID);

            if (set == null)
            {
               set = new HashSet<PagePosition>();
               cursorInfo.getCursorRecords().put(encoding.queueID, set);
            }

            set.add(encoding.position);
         }
         else if (record.userRecordType == JournalStorageManager.PAGE_CURSOR_COMPLETE)
         {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
            encoding.decode(buff);

            Long queueID = new Long(encoding.queueID);
            Long pageNR = new Long(encoding.position.getPageNr());

            if (!cursorInfo.getCompletePages(queueID).add(pageNR))
            {
               System.err.println("Page " + pageNR + " has been already set as complete on queue " + queueID);
            }
         }
         else if (record.userRecordType == JournalStorageManager.PAGE_TRANSACTION)
         {
            if (record.isUpdate)
            {
               PageUpdateTXEncoding pageUpdate = new PageUpdateTXEncoding();

               pageUpdate.decode(buff);
               cursorInfo.getPgTXs().add(pageUpdate.pageTX);
            }
            else
            {
               PageTransactionInfoImpl pageTransactionInfo = new PageTransactionInfoImpl();

               pageTransactionInfo.decode(buff);

               pageTransactionInfo.setRecordID(record.id);
               cursorInfo.getPgTXs().add(pageTransactionInfo.getTransactionID());
            }
         }
      }

      return cursorInfo;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}

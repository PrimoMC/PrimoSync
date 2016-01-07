package net.primomc.PrimoSync;

import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
 * Copyright 2016 Luuk Jacobs

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class AsyncEcoTask extends BukkitRunnable
{
    private ConcurrentMap<UUID, Double> balances;

    public AsyncEcoTask(Map<UUID, Double> balances)
    {
        this.balances = new ConcurrentHashMap<>( balances );
    }
    @Override
    public void run()
    {
        try(Jedis j = PrimoSync.getInstance().getPool().getResource())
        {
            for(UUID uuid : balances.keySet())
            {
                double balance = balances.get(uuid);
                String msg = "Economy;"+uuid.toString() + ";" + balance;
                j.publish( PrimoSync.getInstance().getChannel(), msg );
            }
        }
    }

    public void start()
    {
        this.runTaskAsynchronously( PrimoSync.getInstance() );
    }
}

package net.primomc.PrimoSync;
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

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PrimoSync extends JavaPlugin implements Listener
{
    private static Economy economy;
    private JedisPool pool;
    private static PrimoSync instance;
    private PrimoSyncSubscriber subscriber;

    public static PrimoSync getInstance()
    {
        return instance;
    }

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        initRedis( getConfig().getString( "redis.host" ), getConfig().getInt( "redis.port" ), getConfig().getString( "redis.password" ) );
        instance = this;
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                subscriber = new PrimoSyncSubscriber();
                try ( Jedis jedis = pool.getResource() )
                {
                    jedis.subscribe( subscriber, getChannel() );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    getLogger().severe( "Unable to connect to Redis server. Disabling plugin.." );
                    Bukkit.getServer().getPluginManager().disablePlugin( PrimoSync.getInstance() );
                }
            }
        }.runTaskAsynchronously( this );

        if ( !setupEconomy() )
        {
            getLogger().severe( "Vault not found. PrimoSync requires Vault to function. Disabling plugin.." );
            Bukkit.getServer().getPluginManager().disablePlugin( PrimoSync.getInstance() );
        }

        runEcoInterval();
        getServer().getPluginManager().registerEvents( new PlayerListener(), this );
    }

    private void runEcoInterval()
    {
        int seconds = getConfig().getInt( "sync.economy" );
        if ( seconds < 1 )
        {
            return;
        }
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                log( "Syncing balances for " + Bukkit.getOnlinePlayers().size() + " players." );
                Map<UUID, Double> balances = new HashMap<>();
                for ( Player player : Bukkit.getOnlinePlayers() )
                {
                    double balance = economy.getBalance( player );
                    balances.put( player.getUniqueId(), balance );
                }
                new AsyncEcoTask( balances ).start();
            }
        }.runTaskTimer( this, 0, seconds * 20 );
    }

    private void initRedis( String host, int port, String password )
    {
        if ( ( password == null ) || ( password.isEmpty() ) )
        {
            this.pool = new JedisPool( new JedisPoolConfig(), host, port, 0 );
        }
        else
        {
            this.pool = new JedisPool( new JedisPoolConfig(), host, port, 0, password );
        }
    }

    private boolean setupEconomy()
    {
        if ( getServer().getPluginManager().getPlugin( "Vault" ) == null )
        {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration( Economy.class );
        if ( rsp == null )
        {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static boolean hasEconomy()
    {
        return economy != null;
    }

    public static Economy getEconomy()
    {
        return economy;
    }

    @Override
    public void onDisable()
    {

    }

    public JedisPool getPool()
    {
        return pool;
    }

    public String getChannel()
    {
        return getConfig().getString( "redis.channel", "PrimoSync" );
    }

    public static void log(String message)
    {
        PrimoSync.getInstance().getLogger().info(message);
    }

    public class PrimoSyncSubscriber extends JedisPubSub
    {
        @Override
        public void onMessage( final String channel, final String msg )
        {
            // Needs to be done in the server thread
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    if ( !channel.equals( PrimoSync.getInstance().getChannel() ) )
                    {
                        return;
                    }
                    String[] args = msg.split( ";" );
                    if ( args.length < 1 )
                    {
                        return;
                    }
                    String type = args[0];
                    switch ( type )
                    {
                        case "Economy":
                            handleEconomy( Arrays.copyOfRange( args, 1, args.length ) );
                            break;
                        default:
                    }
                }

                private void handleEconomy( String[] args )
                {
                    if ( args.length < 2 )
                    {
                        return;
                    }
                    UUID uuid = UUID.fromString( args[0] );
                    OfflinePlayer player = Bukkit.getOfflinePlayer( uuid );
                    if ( player == null )
                    {
                        return;
                    }
                    double balance = TypeUtil.getDouble( args[1] );
                    if ( !economy.hasAccount( player ) )
                    {
                        economy.createPlayerAccount( player );
                        economy.depositPlayer( player, balance );
                    }
                    else
                    {
                        double prevBal = economy.getBalance( player );
                        economy.withdrawPlayer( player, prevBal );
                        economy.depositPlayer( player, balance );
                    }
                }
            }.runTask( PrimoSync.getInstance() );
        }

        @Override
        public void onPMessage( String s, String s2, String s3 )
        {
        }

        @Override
        public void onSubscribe( String s, int i )
        {
        }

        @Override
        public void onUnsubscribe( String s, int i )
        {
        }

        @Override
        public void onPUnsubscribe( String s, int i )
        {
        }

        @Override
        public void onPSubscribe( String s, int i )
        {
        }
    }
}

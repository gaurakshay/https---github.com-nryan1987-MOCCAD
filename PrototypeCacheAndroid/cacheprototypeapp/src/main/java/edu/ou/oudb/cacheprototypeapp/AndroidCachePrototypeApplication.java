package edu.ou.oudb.cacheprototypeapp;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.List;

import edu.ou.oudb.cacheprototypeapp.ui.SettingsActivity;
import edu.ou.oudb.cacheprototypelibrary.LRUCacheReplacementManager;
import edu.ou.oudb.cacheprototypelibrary.SemanticQueryCacheContentManager;
import edu.ou.oudb.cacheprototypelibrary.SemanticQueryCacheResolutionManager;
import edu.ou.oudb.cacheprototypelibrary.StandartEstimationCacheContentManager;
import edu.ou.oudb.cacheprototypelibrary.StandartEstimationCacheResolutionManager;
import edu.ou.oudb.cacheprototypelibrary.connection.CloudDataAccessProvider;
import edu.ou.oudb.cacheprototypelibrary.connection.DataAccessProvider;
import edu.ou.oudb.cacheprototypelibrary.connection.StubDataAccessProvider;
import edu.ou.oudb.cacheprototypelibrary.core.cache.Cache;
import edu.ou.oudb.cacheprototypelibrary.core.cache.CacheBuilder;
import edu.ou.oudb.cacheprototypelibrary.core.cachemanagers.DataLoader;
import edu.ou.oudb.cacheprototypelibrary.core.cachemanagers.DecisionalSemanticCacheDataLoader;
import edu.ou.oudb.cacheprototypelibrary.core.cachemanagers.NoCacheDataLoader;
import edu.ou.oudb.cacheprototypelibrary.core.cachemanagers.SemanticCacheDataLoader;
import edu.ou.oudb.cacheprototypelibrary.estimationcache.Estimation;
import edu.ou.oudb.cacheprototypelibrary.metadata.Metadata;
import edu.ou.oudb.cacheprototypelibrary.optimization.OptimizationParameters;
import edu.ou.oudb.cacheprototypelibrary.optimization.Parameter;
import edu.ou.oudb.cacheprototypelibrary.power.HtcOneM7ulPowerReceiver;
import edu.ou.oudb.cacheprototypelibrary.querycache.exception.DownloadDataException;
import edu.ou.oudb.cacheprototypelibrary.querycache.exception.JSONParserException;
import edu.ou.oudb.cacheprototypelibrary.querycache.query.Query;
import edu.ou.oudb.cacheprototypelibrary.querycache.query.QuerySegment;
import edu.ou.oudb.cacheprototypelibrary.utils.StatisticsManager;

public class AndroidCachePrototypeApplication extends Application {
	
	private DataAccessProvider mDataAccessProvider = null;
	
	private DataLoader mDataLoader = null;
	
	private Cache<Query, QuerySegment> mQueryCache = null;
	
	private Cache<Query, Estimation> mMobileEstimationCache = null;
	
	private Cache<Query, Estimation> mCloudEstimationCache = null;
	
	private OptimizationParameters mOptimizationParameters = null;
	
	private List<List<String>> mCurrentQueryResult = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		//In case of error the stubDataAccessProvider is the provider
		Metadata.init(new StubDataAccessProvider());
		HtcOneM7ulPowerReceiver.init(this);
		mDataAccessProvider = new StubDataAccessProvider();
		mOptimizationParameters = new OptimizationParameters();
		setCacheManager();
		setOptimizationParameters();
	}
	
	public DataLoader getDataLoader()
	{
		return mDataLoader;
	}
	
	public void setUseReplacement(boolean useReplacement)
	{
		mDataLoader.setUsingReplacement(useReplacement);
	}

	public void setOptimizationParameters()
	{
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		int importantParameter = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_IMPORTANT_PARAMETER,"0"));
		long timeConstraint = Long.parseLong(sharedPref.getString(SettingsActivity.KEY_PREF_TIME_CONSTRAINT,"0"));
		double moneyConstraint = Double.parseDouble(sharedPref.getString(SettingsActivity.KEY_PREF_MONEY_CONSTRAINT,"0"));
		double energyConstraint = Double.parseDouble(sharedPref.getString(SettingsActivity.KEY_PREF_ENERGY_CONSTRAINT,"0"));
		
		switch(importantParameter)
		{
		case 0:
			mOptimizationParameters.importantParameter = Parameter.TIME;
			break;
		case 1:
			mOptimizationParameters.importantParameter = Parameter.ENERGY;
			break;
		case 2:
			mOptimizationParameters.importantParameter = Parameter.MONEY;
			break;
		}
		
		if (timeConstraint > 0)
		{
			mOptimizationParameters.setTimeConstraint(timeConstraint);
		}
		else
		{
			mOptimizationParameters.setTimeConstraint(Long.MAX_VALUE);
		}
		
		if (moneyConstraint > 0)
		{
			mOptimizationParameters.setMoneyConstraint(moneyConstraint);
		}
		else
		{
			mOptimizationParameters.setMoneyConstraint(Double.POSITIVE_INFINITY);
		}
		
		if (energyConstraint > 0)
		{
			mOptimizationParameters.setEnergyConstraint(energyConstraint);
		}
		else
		{
			mOptimizationParameters.setEnergyConstraint(Double.POSITIVE_INFINITY);
		}
		
	}
	
	/**
	 * Method setting the new Data Access Provider
	 * @throws JSONParserException exception thrown when return result is incorrect
	 * @throws DownloadDataException exception thrown when URL is not correct or there is no connection
	 */
	public void setDataAccessProvider() throws DownloadDataException, JSONParserException 
	{
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String data_access_provider = sharedPref.getString(SettingsActivity.KEY_PREF_DATA_ACCESS_PROVIDER, "");
		
		switch (data_access_provider) {
		case "0":
			mDataAccessProvider = new StubDataAccessProvider();
			break;
		case "1":
			mDataAccessProvider = new CloudDataAccessProvider(getApplicationContext());
			break;
		default:
			mDataAccessProvider = new StubDataAccessProvider();
		}
		
		Metadata.getInstance().setDataAccessProvider(mDataAccessProvider);
		mDataLoader.setDataAccessProvider(mDataAccessProvider);
	}
	
	/**
	 * Set the dataAccessProvider to default
	 */
	public void setDataAccessProviderToDefault()
	{
		mDataAccessProvider = new StubDataAccessProvider();
		Metadata.getInstance().setDataAccessProvider(mDataAccessProvider);
		mDataLoader.setDataAccessProvider(mDataAccessProvider);
        StatisticsManager.createFileWriter();
	}
	
	public void setCacheManager() {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String cache_type = sharedPref.getString(SettingsActivity.KEY_PREF_CACHE_TYPE, "");
		
		switch (cache_type) {
		case "0":
			setNoCacheDataLoader();
			break;
		case "1":
			setSemanticCacheDataLoader();
			break;
		case "2":
			setDecisionalSemanticCacheDataLoader();
			break;
		default:
			setNoCacheDataLoader();
		}
		
	}
	
	public void setCurrentQueryResult(List<List<String>> result)
	{
		mCurrentQueryResult = result;
	}
	
	public List<List<String>> getCurrentQueryResult()
	{
		return mCurrentQueryResult;
	}
	
	public Cache<Query, QuerySegment> getQueryCache()
	{
		return mQueryCache;
	}
	
	public Cache<Query, Estimation> getMobileEstimationCache()
	{
		return mMobileEstimationCache;
	}
	
	public Cache<Query, Estimation> getCloudEstimationCache()
	{
		return mCloudEstimationCache;
	}
	
	private void setNoCacheDataLoader()
	{
		/*build no cache manager*/
		mQueryCache=null;
		mMobileEstimationCache=null;
		mCloudEstimationCache=null;
		mDataLoader = new NoCacheDataLoader(this,mDataAccessProvider);
	}
	
	private void setSemanticCacheDataLoader()
	{
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		int maxQueryCacheSize = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_QUERY_CACHE_SIZE, "100000000"));
		int maxQueryCacheSegments = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_QUERY_CACHE_NUMBER_SEGMENT, "0"));
		boolean useReplacement = sharedPref.getBoolean(SettingsActivity.KEY_PREF_USE_REPLACEMENT,true);
		
		/*build semantic cache manager*/
		mMobileEstimationCache=null;
		mCloudEstimationCache=null;
		// build managers
		SemanticQueryCacheContentManager contentManager = new SemanticQueryCacheContentManager();
		SemanticQueryCacheResolutionManager resolutionManager = new SemanticQueryCacheResolutionManager();
		LRUCacheReplacementManager lruCacheManager = new LRUCacheReplacementManager();
		
		// build query cache
		CacheBuilder<Query,QuerySegment> builder = CacheBuilder.<Query,QuerySegment>newBuilder();
		builder.setCacheContentManager(contentManager);
		builder.setCacheResolutionManager(resolutionManager);
		builder.setCacheReplacementManager(lruCacheManager);
		builder.setMaxSize(maxQueryCacheSize);
		if (maxQueryCacheSegments > 0)//not default
		{
			builder.setMaxSegment(maxQueryCacheSegments);
		}
		mQueryCache = builder.build();
		
		//build cache manager
		mDataLoader = new SemanticCacheDataLoader(this, mDataAccessProvider, mQueryCache, useReplacement);
	}
	
	private void setDecisionalSemanticCacheDataLoader()
	{
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		
		int maxQueryCacheSize = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_QUERY_CACHE_SIZE, "100000000"));
		int maxQueryCacheSegments = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_QUERY_CACHE_NUMBER_SEGMENT, "0"));
		int maxMobileEstimationCacheSize = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_MOBILE_ESTIMATION_CACHE_SIZE, "10000000"));
		int maxMobileEstimationCacheSegments = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_MOBILE_ESTIMATION_CACHE_NUMBER_SEGMENT, "0"));
		int maxCloudEstimationCacheSize = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_CLOUD_ESTIMATION_CACHE_SIZE, "10000000"));
		int maxCloudEstimationCacheSegments = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_MAX_CLOUD_ESTIMATION_CACHE_NUMBER_SEGMENT, "0"));
		boolean useReplacement = sharedPref.getBoolean(SettingsActivity.KEY_PREF_USE_REPLACEMENT,true);
		
		/*build decisional semantic cache data loader*/
		
		// build query managers for query cache
		SemanticQueryCacheContentManager semanticCacheContentManager = new SemanticQueryCacheContentManager();
		SemanticQueryCacheResolutionManager semanticCacheResolutionManager = new SemanticQueryCacheResolutionManager();
		LRUCacheReplacementManager semanticCacheLruCacheManager = new LRUCacheReplacementManager();
		
		// build query managers for mobile estimation cache
		StandartEstimationCacheContentManager mobileEstimationCacheContentManager = new StandartEstimationCacheContentManager();
		StandartEstimationCacheResolutionManager mobileEstimationCacheResolutionManager = new StandartEstimationCacheResolutionManager();
		LRUCacheReplacementManager mobileEstimationCacheLruCacheManager = new LRUCacheReplacementManager();
		
		// build query managers for cloud estimation cache
		StandartEstimationCacheContentManager cloudEstimationCacheContentManager = new StandartEstimationCacheContentManager();
		StandartEstimationCacheResolutionManager cloudEstimationCacheResolutionManager = new StandartEstimationCacheResolutionManager();
		LRUCacheReplacementManager cloudEstimationCacheLruCacheManager = new LRUCacheReplacementManager();
		
		
		//build query cache
		CacheBuilder<Query,QuerySegment> queryCacheBuilder = CacheBuilder.<Query,QuerySegment>newBuilder();
		queryCacheBuilder.setCacheContentManager(semanticCacheContentManager);
		queryCacheBuilder.setCacheResolutionManager(semanticCacheResolutionManager);
		queryCacheBuilder.setCacheReplacementManager(semanticCacheLruCacheManager);
		queryCacheBuilder.setMaxSize(maxQueryCacheSize);
		if (maxQueryCacheSegments > 0)//not default
		{
			queryCacheBuilder.setMaxSegment(maxQueryCacheSegments);
		}
		mQueryCache = queryCacheBuilder.build();
		
		//build mobile estimation cache
		CacheBuilder<Query,Estimation> mobileEstimationCacheBuilder = CacheBuilder.<Query,Estimation>newBuilder();
		mobileEstimationCacheBuilder.setCacheContentManager(mobileEstimationCacheContentManager);
		mobileEstimationCacheBuilder.setCacheResolutionManager(mobileEstimationCacheResolutionManager);
		mobileEstimationCacheBuilder.setCacheReplacementManager(mobileEstimationCacheLruCacheManager);
		mobileEstimationCacheBuilder.setMaxSize(maxMobileEstimationCacheSize);
		if (maxMobileEstimationCacheSegments > 0)//not default
		{
			mobileEstimationCacheBuilder.setMaxSegment(maxMobileEstimationCacheSegments);
		}
		mMobileEstimationCache = mobileEstimationCacheBuilder.build();
		
		//build mobile estimation cache
		CacheBuilder<Query,Estimation> cloudEstimationCacheBuilder = CacheBuilder.<Query,Estimation>newBuilder();
		cloudEstimationCacheBuilder.setCacheContentManager(cloudEstimationCacheContentManager);
		cloudEstimationCacheBuilder.setCacheResolutionManager(cloudEstimationCacheResolutionManager);
		cloudEstimationCacheBuilder.setCacheReplacementManager(cloudEstimationCacheLruCacheManager);
		cloudEstimationCacheBuilder.setMaxSize(maxCloudEstimationCacheSize);
		if (maxCloudEstimationCacheSegments > 0)//not default
		{
			mobileEstimationCacheBuilder.setMaxSegment(maxCloudEstimationCacheSegments);
		}
		mCloudEstimationCache = cloudEstimationCacheBuilder.build();
		
		//build cache manager
		mDataLoader = new DecisionalSemanticCacheDataLoader(this,mDataAccessProvider,mMobileEstimationCache,mCloudEstimationCache,mQueryCache,mOptimizationParameters, useReplacement);
	}
	
}

package net.opusapp.licensing;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.HashSet;
import java.util.Set;

 abstract class IdentityResolver {
	private static final String PREFS_FILE = "net.opusapp.licensing.LicenseChecker";
	private static final String PPEF_USERID = "userId";
	private final Context mContext;
	private final LicenseCheckerCallback mCallback;
    private SharedPreferences mPreferences;
        
    public IdentityResolver(Context context, LicenseCheckerCallback callback){
		mContext = context;
		mCallback = callback;
	    mPreferences = mContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
	}
	
	private String getUserId(){
      return mPreferences.getString(PPEF_USERID, null);
	}
	
	private void setUserId(String userId){
		Editor edit = mPreferences.edit();
		edit.putString(PPEF_USERID, userId);
		edit.apply();
	}
	
	public void getIdentity(IAccountType accountType){
		AccountManager manager = (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);
		Account[] accountList = manager.getAccounts();
		Set<String> possibleAccount = new HashSet<String>();

		for(Account account: accountList)
		{
			if(account.type.equalsIgnoreCase(accountType.getType()))
			{
				possibleAccount.add(account.name);
			}
		}
		
		int countAccounts = possibleAccount.size();
		if(countAccounts > 0){
			String userId = obfuscatedIdentifier((String)possibleAccount.toArray()[0]);
			setUserId(userId);
			onIdentifactionFinished(userId);
		} else if(countAccounts <= 0){
			//No Account with the given account type available
			//Return an application Error
			 mCallback.applicationError(LicenseCheckerCallback.ERROR_NO_ACCOUNT_AVAILABLE);
		} else {
			String preferredUserId = getUserId();
			
			if(preferredUserId != null){
				if(possibleAccount.contains(preferredUserId)){
					String userId = obfuscatedIdentifier(preferredUserId);
					setUserId(userId);
					onIdentifactionFinished(userId);
				}
			}
		}
	}

	private String obfuscatedIdentifier(String identifier){
		return identifier;
	}

	public abstract void onIdentifactionFinished(String userId);
}
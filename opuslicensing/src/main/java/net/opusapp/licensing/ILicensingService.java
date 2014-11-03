package net.opusapp.licensing;

public interface ILicensingService {
	void checkLicense(int nonce, String packageName, String versionCode, String userId, ILicenseResultListener listener);
}

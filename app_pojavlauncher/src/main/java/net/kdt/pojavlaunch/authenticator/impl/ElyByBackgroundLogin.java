package net.kdt.pojavlaunch.authenticator.impl;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Log;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.BackgroundLogin;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.listener.LoginListener;
import net.kdt.pojavlaunch.authenticator.model.OAuthTokenResponse;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

public class ElyByBackgroundLogin implements BackgroundLogin {

    public static final BackgroundLogin.Creator CREATOR =
            ElyByBackgroundLogin::new;

    private static final String AUTH_TOKEN_URL =
            "https://account.ely.by/api/oauth2/v1/token";

    private static final String ACCOUNT_INFO_URL =
            "https://account.ely.by/api/account/v1/info";

    private static final String CLIENT_ID =
            "nether-launcher";

    private static final String REDIRECT_URI =
            "internalredirect://complete";

    private OAuthTokenResponse mOAuthData;
    private ElyAccountInfo mAccountInfo;
    private long mExpiresAt;

    private ElyByBackgroundLogin() {}

    private void acquireAccountDetails(
            @NonNull LoginListener loginListener,
            Callable<Void> continuation,
            String code,
            boolean isRefresh
    ) {
        ProgressLayout.setProgress(
                ProgressLayout.AUTHENTICATE,
                0
        );

        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(2);

            try {
                notifyProgress(loginListener, 1);

                acquireTokens(isRefresh, code);

                notifyProgress(loginListener, 2);

                mAccountInfo =
                        acquireAccountData(mOAuthData.accessToken);

                continuation.call();

            } catch (Exception e) {
                Log.e(
                        "ElyByLogin",
                        "Exception thrown during authentication",
                        e
                );

                Tools.runOnUiThread(
                        () -> loginListener.onLoginError(e)
                );
            }

            ProgressLayout.clearProgress(
                    ProgressLayout.AUTHENTICATE
            );
        });
    }

    private void fillAccount(Account acc) {
        acc.expiresAt = mExpiresAt;
        acc.authType = AuthType.ELY_BY;

        acc.accessToken = mOAuthData.accessToken;
        acc.refreshToken = mOAuthData.refreshToken;

        acc.username = mAccountInfo.username;
        acc.profileId = mAccountInfo.uuid;

        acc.xuid = null;

        acc.updateSkinFace();
    }

    @Override
    public void createAccount(
            @NonNull LoginListener loginListener,
            String code
    ) {
        acquireAccountDetails(
                loginListener,
                () -> {
                    Account account =
                            Accounts.create(this::fillAccount);

                    Tools.runOnUiThread(
                            () -> loginListener.onLoginDone(account)
                    );

                    return null;
                },
                code,
                false
        );
    }

    @Override
    public void refreshAccount(
            @NonNull LoginListener loginListener,
            Account account
    ) {
        acquireAccountDetails(
                loginListener,
                () -> {
                    fillAccount(account);
                    account.save();

                    Tools.runOnUiThread(
                            () -> loginListener.onLoginDone(account)
                    );

                    return null;
                },
                account.refreshToken,
                true
        );
    }

    private void acquireTokens(
            boolean isRefresh,
            String code
    ) throws IOException {

        URL url = new URL(AUTH_TOKEN_URL);

        Log.i(
                "ElyByLogin",
                "Token request, refresh=" + isRefresh
        );

        String formData = CommonLoginUtils.convertToFormData(
                "client_id", CLIENT_ID,
                "redirect_uri", REDIRECT_URI,

                isRefresh
                        ? "refresh_token"
                        : "code",
                code,

                "grant_type",
                isRefresh
                        ? "refresh_token"
                        : "authorization_code"
        );

        mOAuthData =
                CommonLoginUtils.exchangeAuthCode(
                        url,
                        formData
                );

        mExpiresAt =
                mOAuthData.expiresIn * 1000L
                        + System.currentTimeMillis();
    }

    private ElyAccountInfo acquireAccountData(
            String accessToken
    ) throws IOException {

        URL url = new URL(ACCOUNT_INFO_URL);

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestProperty(
                "Authorization",
                "Bearer " + accessToken
        );

        conn.setUseCaches(false);
        conn.connect();

        if (conn.getResponseCode() >= 200
                && conn.getResponseCode() < 300) {

            try (InputStreamReader reader =
                         new InputStreamReader(
                                 conn.getInputStream()
                         )) {

                return Tools.GLOBAL_GSON.fromJson(
                        reader,
                        ElyAccountInfo.class
                );

            } finally {
                conn.disconnect();
            }

        } else {
            throw CommonLoginUtils.getResponseThrowable(conn);
        }
    }

    private void notifyProgress(
            LoginListener listener,
            int step
    ) {
        Tools.runOnUiThread(
                () -> listener.onLoginProgress(step)
        );

        ProgressLayout.setProgress(
                ProgressLayout.AUTHENTICATE,
                step * 50
        );
    }

    private static class ElyAccountInfo {
        public String uuid;
        public String username;
    }
    }

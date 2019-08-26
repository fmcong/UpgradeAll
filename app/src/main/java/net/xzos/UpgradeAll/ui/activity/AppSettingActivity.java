package net.xzos.UpgradeAll.ui.activity;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import net.xzos.UpgradeAll.R;
import net.xzos.UpgradeAll.database.HubDatabase;
import net.xzos.UpgradeAll.database.RepoDatabase;
import net.xzos.UpgradeAll.gson.HubConfig;
import net.xzos.UpgradeAll.server.ServerContainer;
import net.xzos.UpgradeAll.server.app.engine.js.JavaScriptEngine;
import net.xzos.UpgradeAll.server.log.LogUtil;
import net.xzos.UpgradeAll.utils.VersionChecker;

import org.json.JSONException;
import org.json.JSONObject;
import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;

public class AppSettingActivity extends AppCompatActivity {

    private static final LogUtil Log = ServerContainer.AppServer.getLog();
    private static final String TAG = "UpdateItemSetting";
    private static final String[] LogObjectTag = {"Core", TAG};

    private static ArrayList<String> apiSpinnerList = new ArrayList<>();

    private int databaseId;  // 设置页面代表的数据库项目

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_setting);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.add);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        // 获取可能来自修改设置项的请求
        databaseId = getIntent().getIntExtra("database_id", 0);
        // 刷新第三方源列表，获取支持的第三方源列表
        String[] apiSpinnerStringArray = renewApiJsonObject();
        // 修改 apiSpinner
        if (apiSpinnerStringArray.length != 0) {
            Spinner apiSpinner = findViewById(R.id.apiSpinner);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, apiSpinnerStringArray);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            apiSpinner.setAdapter(adapter);
        } else {
            Toast.makeText(AppSettingActivity.this, "请先添加软件源", Toast.LENGTH_LONG).show();
            onBackPressed();
        }
        setSettingItem(); // 设置预置设置项
        // 以下是按键事件
        Button versionCheckButton = findViewById(R.id.statusCheckButton);
        versionCheckButton.setOnClickListener(v -> {
            // 版本检查设置
            JSONObject versionCheckerJsonObject = getVersionChecker();
            VersionChecker versionChecker = new VersionChecker(versionCheckerJsonObject);
            String appVersion = versionChecker.getVersion();
            if (appVersion != null) {
                Toast.makeText(AppSettingActivity.this, "version: " + appVersion, Toast.LENGTH_SHORT).show();
            }
        });
        Button addButton = findViewById(R.id.saveButton);
        addButton.setOnClickListener(v -> {
            addButton.setEnabled(false);
            addApp();
            addButton.setEnabled(true);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addApp() {
        EditText editName = findViewById(R.id.editName);
        EditText editUrl = findViewById(R.id.editUrl);
        String name = editName.getText().toString();
        String url = editUrl.getText().toString();
        Spinner apiSpinner = findViewById(R.id.apiSpinner);
        int apiNum = apiSpinner.getSelectedItemPosition();
        JSONObject versionChecker = getVersionChecker();
        ProgressDialog progressDialog = new ProgressDialog(this);
        new Thread(() -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                // 弹出等待框
                progressDialog.setTitle("正在添加，请稍等");
                progressDialog.setMessage("Loading...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            });
            // 添加数据库
            boolean addRepoSuccess = addRepoDatabase(databaseId, name, apiNum, url, versionChecker);
            if (addRepoSuccess) {
                ServerContainer.AppServer.getAppManager().setApp(databaseId);
            }
            // 强行刷新被修改的子项
            new Handler(Looper.getMainLooper()).post(() -> {
                // 取消等待框
                if (!addRepoSuccess) {
                    Toast.makeText(AppSettingActivity.this, "什么？数据库添加失败！", Toast.LENGTH_LONG).show();
                }
                progressDialog.cancel();
                onBackPressed();  // 跳转主页面
            });
        }).start();

    }

    private void setSettingItem() {
        // 如果是设置修改请求，设置预置设置项
        if (databaseId != 0) {
            RepoDatabase database = LitePal.find(RepoDatabase.class, databaseId);
            EditText editName = findViewById(R.id.editName);
            editName.setText(database.getName());
            EditText editUrl = findViewById(R.id.editUrl);
            editUrl.setText(database.getUrl());
            Spinner apiSpinner = findViewById(R.id.apiSpinner);
            String apiUuid = database.getApiUuid();
            // 设置 apiSpinner 位置
            int spinnerIndex = apiSpinnerList.indexOf(apiUuid);
            if (spinnerIndex != -1) apiSpinner.setSelection(spinnerIndex);
            Spinner versionCheckSpinner = findViewById(R.id.versionCheckSpinner);
            JSONObject versionChecker = database.getVersionChecker();
            String versionCheckerApi = "";
            String versionCheckerText = "";
            try {
                versionCheckerApi = versionChecker.getString("api");
                versionCheckerText = versionChecker.getString("text");
            } catch (JSONException e) {
                Log.e(LogObjectTag, TAG, String.format("onCreate: 数据库损坏！  versionChecker: %s", versionChecker));
            }
            switch (versionCheckerApi.toLowerCase()) {
                case "app":
                    versionCheckSpinner.setSelection(0);
                    break;
                case "magisk":
                    versionCheckSpinner.setSelection(1);
                    break;
            }
            EditText editVersionCheckText = findViewById(R.id.editVersionCheckText);
            editVersionCheckText.setText(versionCheckerText);
        }
    }

    private JSONObject getVersionChecker() {
        // 获取versionChecker
        JSONObject versionChecker = new JSONObject();
        Spinner versionCheckSpinner = findViewById(R.id.versionCheckSpinner);
        EditText editVersionCheckText = findViewById(R.id.editVersionCheckText);
        String versionCheckerText = editVersionCheckText.getText().toString();
        String versionCheckerApi = versionCheckSpinner.getSelectedItem().toString();
        switch (versionCheckerApi) {
            case "APP 版本":
                versionCheckerApi = "APP";
                break;
            case "Magisk 模块":
                versionCheckerApi = "Magisk";
                break;
            case "自定义 Shell 命令":
                versionCheckerApi = "Shell";
                break;
            case "自定义 Shell 命令（ROOT）":
                versionCheckerApi = "Shell_ROOT";
                break;
        }
        try {
            versionChecker.put("api", versionCheckerApi);
            versionChecker.put("text", versionCheckerText);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return versionChecker;
    }

    boolean addRepoDatabase(int databaseId, String name, int apiNum, @NonNull String URL, JSONObject versionChecker) {
        // 数据处理
        String uuid = apiSpinnerList.get(apiNum);
        if (URL.length() != 0 && uuid != null) {
            // 判断url是否多余
            if (URL.substring(URL.length() - 1).equals("/")) {
                URL = URL.substring(0, URL.length() - 1);
            }
            if (name.length() == 0) {
                // 自定义源
                List<HubDatabase> hubDatabase = LitePal.findAll(HubDatabase.class);
                for (HubDatabase hubItem : hubDatabase) {
                    if (hubItem.getUuid().equals(uuid)) {
                        String jsCode = hubItem.getExtraData().getJavascript();
                        if (jsCode == null)
                            Log.e(LogObjectTag, TAG, "未找到 js 脚本");
                        HubConfig hubConfig = hubItem.getHubConfig();
                        if (hubConfig != null) {
                            String tool = null;
                            if (hubConfig.getWebCrawler() != null) {
                                tool = hubConfig.getWebCrawler().getTool();
                            }
                            if (tool != null && tool.toLowerCase().equals("javascript")) {
                                String[] logObjectTag = {"TEMP", "0"};
                                name = new JavaScriptEngine.Builder(logObjectTag, URL, jsCode).build().getDefaultName();
                            }
                        }
                        break;
                    }
                }
            }
            // 修改数据库
            RepoDatabase repoDatabase = LitePal.find(RepoDatabase.class, databaseId);
            if (repoDatabase == null) repoDatabase = new RepoDatabase();
            // 开启数据库
            Spinner apiSpinner = findViewById(R.id.apiSpinner);
            String api = apiSpinner.getSelectedItem().toString();
            // 将数据存入 RepoDatabase 数据库
            repoDatabase.setName(name);
            repoDatabase.setApi(api);
            repoDatabase.setApiUuid(uuid);
            repoDatabase.setUrl(URL);
            repoDatabase.setVersionChecker(versionChecker);
            repoDatabase.save();
            // 为 databaseId 赋值
            this.databaseId = repoDatabase.getId();
            return true;
        }
        return false;
    }

    @NonNull
    private String[] renewApiJsonObject() {
        // api接口名称列表
        // 清空 apiSpinnerList
        List<String> nameStringList = new ArrayList<>();
        // 获取自定义源
        List<HubDatabase> hubList = LitePal.findAll(HubDatabase.class);  // 读取 hub 数据库
        for (int i = 0; i < hubList.size(); i++) {
            HubDatabase hubItem = hubList.get(i);
            String name = hubItem.getName();
            String apiUuid = hubItem.getUuid();
            nameStringList.add(name);
            // 记录可用的api UUID
            apiSpinnerList.add(apiUuid);
        }
        return nameStringList.toArray(new String[0]);
    }
}
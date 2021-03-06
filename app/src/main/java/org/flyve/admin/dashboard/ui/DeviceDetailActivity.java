package org.flyve.admin.dashboard.ui;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;

import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.flyve.admin.dashboard.R;
import org.flyve.admin.dashboard.ping.NumberPhoneAdapter;
import org.flyve.admin.dashboard.ping.NumberPhoneCardView;
import org.flyve.admin.dashboard.utils.FlyveLog;
import org.mobicents.protocols.ss7.map.api.smstpdu.SmsStatusReportTpdu;
import org.mobicents.protocols.ss7.map.api.smstpdu.SmsTpdu;
import org.mobicents.protocols.ss7.map.api.smstpdu.SmsTpduType;
import org.mobicents.protocols.ss7.map.api.smstpdu.Status;
import org.mobicents.protocols.ss7.map.smstpdu.SmsTpduImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DeviceDetailActivity extends AppCompatActivity implements editSmsDialog.DialogListener {

    final byte[] payload = new byte[]{0x0A, 0x06, 0x03, (byte) 0xB0, (byte) 0xAF, (byte) 0x82, 0x03, 0x06, 0x6A, 0x00, 0x05};
    byte[] lastSendResultPDU = new byte[0];

    final String TAG = "Ping SMS";

    final String SENT = "pingsms.sent";
    final String DELIVER = "pingsms.deliver";

    final IntentFilter sentFilter = new IntentFilter(SENT);
    final IntentFilter deliveryFilter = new IntentFilter(DELIVER);
    final IntentFilter wapDeliveryFilter = new IntentFilter("android.provider.Telephony.WAP_PUSH_DELIVER");

    SharedPreferences preferences;
    public final static String PREF_DATA_SMS_STORE = "pref_data_sms_store";
    public final static String PREF_LAST_NUMBER = "pref_last_number";
    public final static String PREF_RECEIVE_DATA_SMS = "pref_receive_data_sms";

    PendingIntent sentPI;
    PendingIntent deliveryPI;

    EditText phoneNumberPing;
    TextView statusText, resultText, date;
    ImageButton resultPduDetails;

    private ArrayList<NumberPhoneCardView> mNumberList;

    private RecyclerView mRecyclerViewCard;
    private RecyclerView.LayoutManager mLayoutManagerCard;
    private NumberPhoneAdapter mAdapterCard;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        createNumberPhoneList();
        buildRecyclerView();


        //SMS SILENT PING
        phoneNumberPing = findViewById(R.id.phoneNumberPing);
        statusText = findViewById(R.id.sendStatus);
        resultText = findViewById(R.id.resultStatus);
        resultPduDetails = findViewById(R.id.resultPduDetails);
        date = findViewById(R.id.date);

        findViewById(R.id.sendButton).setOnClickListener(e -> {
            final String phoneNum = phoneNumberPing.getText().toString();
            if (DeviceDetailActivity.this.checkPermissions() && !TextUtils.isEmpty(phoneNum) && Patterns.PHONE.matcher(phoneNum).matches()) {
                resultText.setText(null);
                SmsManager.getDefault().sendDataMessage(phoneNum, null, (short) 9200, payload, sentPI, deliveryPI);

                //Get  date
                Date hourdateFormat = new Date();
                date.setText("Date " + hourdateFormat);
            }
        });

        resultPduDetails.setOnClickListener(e -> showPduInfoDialog());

        sentPI = PendingIntent.getBroadcast(this, 0x1337, new Intent(SENT), PendingIntent.FLAG_CANCEL_CURRENT);
        deliveryPI = PendingIntent.getBroadcast(this, 0x1337, new Intent(DELIVER), PendingIntent.FLAG_CANCEL_CURRENT);

        //TOOLBAR

        Toolbar toolbar = findViewById(R.id.toolbar);

        toolbar.inflateMenu(R.menu.device_menu_toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            try {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            } catch(Exception ex) {
                FlyveLog.e(ex.getMessage());
            }

            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }


    }

    /*** INFLATE MENU AND ADD OR REMOVE NEW LIST SIMCARDS ***/
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.device_menu_toolbar, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.itemAdd:
                Toast.makeText(this, "Add", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.itemDelete:
                Toast.makeText(this,"Delete", Toast.LENGTH_SHORT).show();
                return true;
            default:
                    return super.onOptionsItemSelected(item);
        }
    }

    /**
     * CREATE THE CARD VIEW AND RECYCLER VIEW
     * @param position
     */

    public void changeNumber (int position, String number){
        mNumberList.get(position).changePhoneNumber(number);
        mAdapterCard.notifyDataSetChanged();
    }

    public void changeIMEI(int position, String imei){
        mNumberList.get(position).changeIMEI(imei);
        mAdapterCard.notifyDataSetChanged();
    }

    public void changeSimCard(int position, String simcardnumber){
        mNumberList.get(position).changeSimCardNumber(simcardnumber);
        mAdapterCard.notifyDataSetChanged();
    }

    public void changeLastContact (int position, String text) {
        mNumberList.get(position).changeContactText(text);
        mAdapterCard.notifyDataSetChanged();
    }

    public void createNumberPhoneList(){
        mNumberList = new ArrayList<>();
        mNumberList.add(new NumberPhoneCardView("635207705","IMEI","SimCard1", "Last Contact"));
        mNumberList.add(new NumberPhoneCardView("Add Number", "add IMEI","Simcard","Last Contact"));
    }

    public void buildRecyclerView(){
        mRecyclerViewCard = findViewById(R.id.recycler_view);
        mRecyclerViewCard.setHasFixedSize(true);
        mLayoutManagerCard = new LinearLayoutManager(this);
        mAdapterCard = new NumberPhoneAdapter(mNumberList);

        mRecyclerViewCard.setLayoutManager(mLayoutManagerCard);
        mRecyclerViewCard.setAdapter(mAdapterCard);

        mAdapterCard.setOnItemClickListener(new NumberPhoneAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(int position) {
                Toast.makeText(DeviceDetailActivity.this, "editing", Toast.LENGTH_SHORT).show();
                openDialog();
            }

            @Override
            public void onPingClick(int position) {
                final String phoneNum = mNumberList.get(position).getPhone();
                if (checkPermissions() && !TextUtils.isEmpty(phoneNum) && Patterns.PHONE.matcher(phoneNum).matches()) {
                    resultText.setText(null);
                    SmsManager.getDefault().sendDataMessage(phoneNum, null, (short) 9200, payload, sentPI, deliveryPI);

                    //Get  date
                    Date hourdateFormat = new Date();
                    String finalDate = "Last Contact " + hourdateFormat;
                    changeLastContact(position, finalDate);
                }
            }

            @Override
            public void onCallClick(int position) {
                final String phoneNum = mNumberList.get(position).getPhone();
                String dial = "tel: " + phoneNum;
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(dial)));
            }

        });

    }

    /**
     * OPEN THE DIALOG
     */

    @Override
    public void applyTexts(String numberDialog, String imeiDialog, String simcardDialog) {
        // Put the code to change the cardview's textview,
        // for example : exampletextview.setText(numberDialog);
        // for example : changeNumber(position, numberDialog);
    }

    public void openDialog(){
        editSmsDialog editSmsDialog = new editSmsDialog();
        editSmsDialog.show(getSupportFragmentManager(), "edit sms dialog");
    }

    /**
     *  SEND SMS SILENT PING
     *
     */
    boolean checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        int sendSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        int readPhonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        int receiveSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS);

        if (sendSmsPermission != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.SEND_SMS);
        }

        if (readPhonePermission != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (receiveSmsPermission != PackageManager.PERMISSION_GRANTED /*&& preferences.getBoolean(PREF_RECEIVE_DATA_SMS, false)*/) {
            missingPermissions.add(Manifest.permission.RECEIVE_SMS);
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), 1);
            return false;
        }

        return true;
    }

    String getLogBytesHex(byte[] array) {
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(String.format("0x%02X ", b));
        }
        return sb.toString();
    }


    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(br, sentFilter);
        registerReceiver(br, deliveryFilter);
        registerReceiver(br, wapDeliveryFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(br);
    }

    public void showPduInfoDialog() {
        if (lastSendResultPDU == null || lastSendResultPDU.length == 0) {
            resultPduDetails.setVisibility(View.INVISIBLE);
            return;
        }

        SmsTpdu parsedTpdu = getSmsTpdu(lastSendResultPDU);

        AlertDialog dialog = (new AlertDialog.Builder(this))
                .setTitle("Result PDU details")
                .setMessage(parsedTpdu != null ? parsedTpdu.toString() : "N/A")
                .setCancelable(true)
                .setNeutralButton("Close", (dialog1, which) -> dialog1.dismiss())
                .create();

        dialog.show();
    }

    @Nullable
    public SmsTpdu getSmsTpdu(byte[] pduBytes) {
        SmsTpdu result = null;
        try {
            result = SmsTpduImpl.createInstance(pduBytes, false, null);
        } catch (Exception e) {
            Log.d(TAG, "getSmsTpdu:1", e);
        }
        if (result == null) {
            try {
                byte[] pduWithoutSCA = Arrays.copyOfRange(pduBytes, pduBytes[0] + 1, pduBytes.length);
                result = SmsTpduImpl.createInstance(pduWithoutSCA, false, null);
            } catch (Exception e) {
                Log.d(TAG, "getSmsTpdu:2", e);
            }
        }
        return result;
    }

    BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "intent: " + ((intent == null || intent.getAction() == null) ? "null" : intent.getAction()));
            Log.e(TAG, "result: " + getResultCode());
            Log.e(TAG, "pdu (if any): " + ((intent != null && intent.hasExtra("pdu")) ? getLogBytesHex(intent.getByteArrayExtra("pdu")) : ""));

            if (intent == null) {
                return;
            }

            if (SENT.equalsIgnoreCase(intent.getAction())) {
                statusText.setText((getResultCode() == RESULT_OK ? R.string.sent : R.string.notsent));
                resultText.setText(null);
            } else if (DELIVER.equalsIgnoreCase(intent.getAction())) {
                boolean delivered = false;
                if (intent.hasExtra("pdu")) {
                    byte[] pdu = intent.getByteArrayExtra("pdu");
                    if (pdu != null && pdu.length > 1) {
                        lastSendResultPDU = pdu;
                        resultPduDetails.setVisibility(View.VISIBLE);
                        SmsTpdu parsedResultPdu = getSmsTpdu(pdu);
                        if (parsedResultPdu != null) {
                            Log.d(TAG, parsedResultPdu.toString());
                            delivered = parsedResultPdu.getSmsTpduType().equals(SmsTpduType.SMS_STATUS_REPORT) && ((SmsStatusReportTpdu) parsedResultPdu).getStatus().getCode() == Status.SMS_RECEIVED;
                        } else {
                            String resultPdu = getLogBytesHex(pdu).trim();
                            delivered = "00".equalsIgnoreCase(resultPdu.substring(resultPdu.length() - 2));
                        }
                    }
                }
                resultText.setText(delivered ? R.string.delivered : R.string.offline);
            }

        }
    };
}

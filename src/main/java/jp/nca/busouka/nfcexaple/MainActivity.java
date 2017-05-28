package jp.nca.busouka.nfcexaple;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcF;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private IntentFilter[] intentFilters;
    private String[][] techListsArray;
    NfcAdapter mAdapter;
    private PendingIntent pendingIntent;
    private NfcReader nfcReader = new NfcReader();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pendingIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);

        try {
            ndef.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException(e);
        }
        intentFilters = new IntentFilter[]{ndef};

        techListsArray = new String[][] {
                new String[] {NfcF.class.getName()}
        };

        mAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinearLayout ll = (LinearLayout) findViewById(R.id.main);
        ll.removeAllViews();
        if (!mAdapter.isEnabled()) {
            TextView tv = new TextView(this);
            tv.setText("NFC を ON にしてください");
            ll.addView(tv);
            Button button = new Button(this);
            button.setText("設定画面に行く");
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                }
            });
            ll.addView(button);
        } else {
            TextView tv = new TextView(this);
            tv.setText("OK");
            ll.addView(tv);
        }
        mAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techListsArray);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        LinearLayout ll = (LinearLayout) findViewById(R.id.main);
        ll.removeAllViews();

        if (tag == null) {
            return;
        } else {
            NfcF nfc = NfcF.get(tag);
            byte[] idm = tag.getId();
            try {
                nfc.connect();
                byte[] req = readWithoutEncryption(idm, 10);
                Log.d(TAG, "req: " + req);
                byte[] res = nfc.transceive(req);
                Log.d(TAG, "res: " + res);
                List<Rireki> list = parse(res);
//                int[] data = new int[list.size()];
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    sb.append(list.get(i));
                    sb.append(System.getProperty("line.separator"));
                }
                Intent nextIntent = new Intent(this, ListActivity.class);
                nextIntent.putExtra("rirekis", sb.toString());
                startActivity(nextIntent);
                nfc.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.disableForegroundDispatch(this);
    }

    private byte[] readWithoutEncryption(byte[] idm, int size)
            throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(100);

        bout.write(0);           // データ長バイトのダミー
        bout.write(0x06);        // Felicaコマンド「Read Without Encryption」
        bout.write(idm);         // カードID 8byte
        bout.write(1);           // サービスコードリストの長さ(以下２バイトがこの数分繰り返す)
        bout.write(0x0f);        // 履歴のサービスコード下位バイト
        bout.write(0x09);        // 履歴のサービスコード上位バイト
        bout.write(size);        // ブロック数
        for (int i = 0; i < size; i++) {
            bout.write(0x80);    // ブロックエレメント上位バイト 「Felicaユーザマニュアル抜粋」の4.3項参照
            bout.write(i);       // ブロック番号
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length; // 先頭１バイトはデータ長
        Log.d(TAG, "req: " + toHex(msg));
        return msg;
    }

    private List<Rireki> parse(byte[] res) throws Exception {
        // res[0] = データ長
        // res[1] = 0x07
        // res[2〜9] = カードID
        // res[10,11] = エラーコード。0=正常。
        if (res[10] != 0x00) throw new RuntimeException("Felica error.");

        // res[12] = 応答ブロック数
        // res[13+n*16] = 履歴データ。16byte/ブロックの繰り返し。
        int size = res[12];
        String str = "";
        List<Rireki> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            // 個々の履歴の解析。
            Rireki rireki = Rireki.parse(res, 13 + i * 16);
            Log.d(TAG, rireki.toString());
            list.add(rireki);
        }
        return list;
    }

    private String toHex(byte[] id) {
        StringBuilder sbuf = new StringBuilder();
        for (int i = 0; i < id.length; i++) {
            String hex = "0" + Integer.toString((int) id[i] & 0x0ff, 16);
            if (hex.length() > 2)
                hex = hex.substring(1, 3);
            sbuf.append(" " + i + ":" + hex);
        }
        return sbuf.toString();
    }
}

package jp.nca.busouka.nfcexaple;

import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class NfcReader {
    private static final String TAG = "NfcReader";

    public String read(Tag tag) {
        NfcF nfc = NfcF.get(tag);
        byte[] idm = tag.getId();
        try {
            byte[] req = readWithoutEncryption(idm, 10);
            Log.d(TAG, "req: " + req);
            nfc.connect();
            byte[] res = nfc.transceive(req);
            Log.d(TAG, "res: " + res);
            return parse(res);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
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

    private String parse(byte[] res) throws Exception {
        // res[0] = データ長
        // res[1] = 0x07
        // res[2〜9] = カードID
        // res[10,11] = エラーコード。0=正常。
        if (res[10] != 0x00) throw new RuntimeException("Felica error.");

        // res[12] = 応答ブロック数
        // res[13+n*16] = 履歴データ。16byte/ブロックの繰り返し。
        int size = res[12];
        String str = "";
        for (int i = 0; i < size; i++) {
            // 個々の履歴の解析。
            Rireki rireki = Rireki.parse(res, 13 + i * 16);
            str += rireki.toString() +"\n";
        }
        Log.d(TAG, str);
        return str;
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
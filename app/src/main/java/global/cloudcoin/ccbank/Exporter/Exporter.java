package global.cloudcoin.ccbank.Exporter;


import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;

import global.cloudcoin.ccbank.core.AppCore;
import global.cloudcoin.ccbank.core.CallbackInterface;
import global.cloudcoin.ccbank.core.CloudCoin;
import global.cloudcoin.ccbank.core.Config;
import global.cloudcoin.ccbank.core.GLogger;
import global.cloudcoin.ccbank.core.RAIDA;
import global.cloudcoin.ccbank.core.Servant;

public class Exporter extends Servant {
    String ltag = "Exporter";
    ExporterResult er;


    public Exporter(String rootDir, GLogger logger) {
        super("Exporter", rootDir, logger);
    }

    public void launch(CallbackInterface icb) {
        this.cb = icb;
    }

    public void launch(int type, int amount, String tag, CallbackInterface icb) {
        this.cb = icb;

        final int ftype = type;
        final int famount = amount;
        final String ftag = tag;

        er = new ExporterResult();
        coinsPicked = new ArrayList<CloudCoin>();
        launchThread(new Runnable() {
            @Override
            public void run() {
                logger.info(ltag, "RUN Exporter");

                doExport(ftype, null, amount, ftag);
            }
        });
    }
    
    public void launch(int type, int[] values, String tag, CallbackInterface icb) {
        this.cb = icb;

        final int ftype = type;
        final int[] fvalues = values;
        final String ftag = tag;

        er = new ExporterResult();
        coinsPicked = new ArrayList<CloudCoin>();
        valuesPicked = new int[AppCore.getDenominations().length];

        for (int i = 0; i < valuesPicked.length; i++)
            valuesPicked[i] = 0;

        launchThread(new Runnable() {
            @Override
            public void run() {
                logger.info(ltag, "RUN Exporter");

                doExport(ftype, fvalues, 0, ftag);
            }
        });
    }

    public void doExport(int type, int[] values, int amount, String tag) {
        if (tag.equals(""))
            tag = Config.DEFAULT_TAG;

        if (tag.indexOf('.') != -1 || tag.indexOf('/') != -1 || tag.indexOf('\\') != -1) {
            logger.error(ltag, "Invalid tag");
            er.status = ExporterResult.STATUS_ERROR;
            if (cb != null)
                cb.callback(er);
        }

        String fullExportPath = AppCore.getUserDir(Config.DIR_EXPORT, user);
        String fullFrackedPath = AppCore.getUserDir(Config.DIR_FRACKED, user);
        String fullBankPath = AppCore.getUserDir(Config.DIR_BANK, user);

        System.out.println("xxx"+amount);
        if (values != null) {
            if (values.length != AppCore.getDenominations().length) {
                logger.error(ltag, "Invalid params");
                er.status = ExporterResult.STATUS_ERROR;
                if (cb != null)
                    cb.callback(er);

                return;
            }

            if (!pickCoinsInDir(fullBankPath, values)) {
                logger.debug(ltag, "Not enough coins in the bank dir");
                if (!pickCoinsInDir(fullFrackedPath, values)) {
                    logger.error(ltag, "Not enough coins in the Fracked dir");
                    er.status = ExporterResult.STATUS_ERROR;
                    if (cb != null)
                        cb.callback(er);

                    return;
                }
            }
        } else {
            if (!pickCoinsAmountInDir(fullBankPath, amount)) {
                logger.debug(ltag, "Not enough coins in the bank dir for amount " + amount);
                er.status = ExporterResult.STATUS_ERROR;
                    if (cb != null)
                        cb.callback(er);
                    
              /*  if (!pickCoinsAmountInDir(fullFrackedPath, amount)) {
                    logger.error(ltag, "Not enough coins in the Fracked dir");
                    er.status = ExporterResult.STATUS_ERROR;
                    if (cb != null)
                        cb.callback(er);

                    return;
                }  
                      */
            }
        }

        if (type == Config.TYPE_STACK) {
            if (!exportStack(fullExportPath, tag)) {
                er.status = ExporterResult.STATUS_ERROR;
                if (cb != null)
                    cb.callback(er);

                return;
            }
        } else if (type == Config.TYPE_JPEG) {
            if (!exportJpeg(fullExportPath, user, tag)) {
                er.status = ExporterResult.STATUS_ERROR;
                if (cb != null)
                    cb.callback(er);

                return;
            }
        } else {
            logger.error(ltag, "Unsupported format");
            er.status = ExporterResult.STATUS_ERROR;
            if (cb != null)
                cb.callback(er);

            return;
        }

        er.status = ExporterResult.STATUS_FINISHED;
        if (cb != null)
            cb.callback(er);

        logger.info(ltag, "EXPORTTT="+fullExportPath);
    }

    private void deletePickedCoins() {
        for (CloudCoin cc : coinsPicked) {
            AppCore.deleteFile(cc.originalFile);
        }
    }

    private boolean exportJpeg(String dir, String user, String tag) {
        String templateDir = AppCore.getUserDir(Config.DIR_TEMPLATES, user);
        String fileName;
        StringBuilder sb;

        sb = new StringBuilder();

        byte[] bytes;
        for (CloudCoin cc : coinsPicked) {
            logger.debug(ltag, "Exporting: " + cc.sn);
            fileName = templateDir + File.separator + "jpeg" + cc.getDenomination() + ".jpg";

            bytes = AppCore.loadFileToBytes(fileName);
            if (bytes == null) {
                logger.error(ltag, "Failed to load template");
                return false;
            }

            logger.info(ltag, "Loaded: " + bytes.length);

            // Header
            sb.append(Config.JPEG_MARKER);

            // Ans
            for (int i = 0; i < RAIDA.TOTAL_RAIDA_COUNT; i++) {
                sb.append(cc.ans[i]);
            }

            // AOID
            sb.append("00000000000000000000000000000000");

            // PownString
            for (int i = 0; i < RAIDA.TOTAL_RAIDA_COUNT; i++) {
                sb.append("0");
            }

            // Append trailing zero
            if (RAIDA.TOTAL_RAIDA_COUNT % 2 != 0)
                sb.append("0");

            // Append HC
            sb.append("00");

            // Append ED
            sb.append("97E2");

            // NN
            sb.append("0" + cc.nn);

            // SN
            sb.append(AppCore.padString(Integer.toHexString(cc.sn).toUpperCase(), 6, '0'));


            byte[] ccArray = AppCore.hexStringToByteArray(sb.toString());
            int offset = 20;
            for (int j =0; j < ccArray.length; j++) {
                bytes[offset + j] = ccArray[j];
            }

            fileName = cc.getDenomination() + ".CloudCoin." + System.currentTimeMillis() + "." + tag + ".jpeg";
            fileName = dir + File.separator + fileName;

            logger.info(ltag, "saving bytes " + bytes.length);
            if (!AppCore.saveFileFromBytes(fileName, bytes)) {
                logger.error(ltag, "Failed to write file");
                return false;
            }

            er.exportedFileNames.add(fileName);
        }

        deletePickedCoins();

        return true;
    }


    private boolean exportStack(String dir, String tag) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        int total = 0;
        String fileName;

        sb.append("{\"cloudcoin\": [");
        for (CloudCoin cc : coinsPicked) {
            if (!first)
                sb.append(",");

            sb.append(cc.getSimpleJson());
            first = false;

            total += cc.getDenomination();
        }

        sb.append("]}");

        fileName = total + ".CloudCoin." + System.currentTimeMillis() + "." + tag + ".stack";
        fileName = dir + File.separator + fileName;

        if (!AppCore.saveFile(fileName, sb.toString())) {
            logger.error(ltag, "Failed to save file " + fileName);
            return false;
        }

        er.exportedFileNames.add(fileName);

        deletePickedCoins();

        return true;
    }

}

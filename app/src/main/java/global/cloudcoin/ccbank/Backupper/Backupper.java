package global.cloudcoin.ccbank.Backupper;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import global.cloudcoin.ccbank.LossFixer.LossFixerResult;
import global.cloudcoin.ccbank.core.AppCore;
import global.cloudcoin.ccbank.core.CallbackInterface;
import global.cloudcoin.ccbank.core.Config;
import global.cloudcoin.ccbank.core.GLogger;
import global.cloudcoin.ccbank.core.Servant;

public class Backupper extends Servant {
    String ltag = "Backupper";
    BackupperResult br;
    String user;


    public Backupper(String rootDir, GLogger logger) {
        super("Backupper", rootDir, logger);
    }

    public void launch(String user, String destDir, CallbackInterface icb) {
        this.cb = icb;

        final String fdestDir = destDir;
        br = new BackupperResult();
        this.user = user;

        launchThread(new Runnable() {
            @Override
            public void run() {
                logger.info(ltag, "RUN Backupper");

                doBackup(fdestDir);
                if (cb != null)
                    cb.callback(br);
            }
        });
    }

    public void doBackup(String destDir) {
        logger.info(ltag, "Backup! " + destDir);
        File f;

        f = new File(destDir);
        if (!f.exists()) {
            logger.error(ltag, "Failed to access dir " + destDir);
            br.status = BackupperResult.STATUS_ERROR;
            return;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MMM-d h:mma");
        Date date = new Date();
        String bdir = destDir + File.separator + user + File.separator + "CloudCoinBackup-" + dateFormat.format(date);

        logger.info(ltag, "Creating backup dir " + bdir);

        f = new File(bdir);
        f.mkdirs();
        if (!f.exists()) {
            logger.error(ltag, "Failed to access dir " + destDir);
            br.status = BackupperResult.STATUS_ERROR;
            return;
        }

        backupDir(bdir, Config.DIR_BANK);
        backupDir(bdir, Config.DIR_FRACKED);
        backupDir(bdir, Config.DIR_GALLERY);
        backupDir(bdir, Config.DIR_VAULT);
        backupDir(bdir, Config.DIR_LOST);
        backupDir(bdir, Config.DIR_MIND);

        if (br.status != BackupperResult.STATUS_ERROR)
            br.status = BackupperResult.STATUS_FINISHED;
    }

    public void backupDir(String destDir, String srcDir) {
        String dstFile;
        logger.info(ltag, "Backupping " + srcDir);

        destDir = destDir + File.separator + srcDir;
        File f = new File(destDir);
        if (!f.mkdir()) {
            logger.error(ltag, "Failed to create dir: " + destDir);
            br.status = BackupperResult.STATUS_ERROR;
            return;
        }

        srcDir = AppCore.getUserDir(srcDir, this.user);
        File dirObj = new File(srcDir);
        for (File file: dirObj.listFiles()) {
            if (file.isDirectory())
                continue;

            dstFile = destDir + File.separator + file.getName();
            logger.info(ltag, "Backupping " + file.toString() + " to " + dstFile);
            if (!AppCore.copyFile(file.toString(), dstFile)) {
                br.status = BackupperResult.STATUS_ERROR;
                logger.error(ltag,"Failed to copy file to: " + dstFile);
                continue;
            }
        }
    }

}

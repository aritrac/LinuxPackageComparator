import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DoSSH {
    //Bootstrap Properties. Please DONT modify
    private static String centosPath = "C:\\JARS\\CA_API_PlatformUpdate_64bit_v9.X-CentOS-2018-02-15";
    private static String rhelPath = "C:\\JARS\\CA_API_PlatformUpdate_64bit_v9.X-RHEL-2018-02-15";
    private static List<String> rpmList = new ArrayList<>();
    private static List<String> basePackageRpms = new ArrayList();
    private static List<String> plusPackageRpms = new ArrayList();
    private static List<PkgInformation> pkgInfoList = new ArrayList();
    private static List<String> packagesLeftForUpdateCheck = new ArrayList<>();
    private static List<String> fullPackagesLeftForUpdateCheck = new ArrayList<>();
    private static final String centosAzureFile = "RPMComparator/9_2_GA_Centos_Azure_52_163_118_9.txt";
    private static final String centosAMIFile = "RPMComparator/9_2_GA_Centos_AMI_35.161.252.37.txt";
    private static final String centosOVAFile = "RPMComparator/9_2_GA_Centos_OVA_10_130_5_160.txt";
    private static final String mirrorBaseURL = "http://centos6-64-repo-mirror.l7tech.com/base/Packages/";
    private static final String mirrorPlusURL = "http://centos6-64-repo-mirror.l7tech.com/centosplus/Packages/";
    private static final String mirrorPlusFile = "RPMComparator/PLUSPACKAGELIST.txt";
    private static final String mirrorBaseFile = "RPMComparator/BASEPACKAGELIST.txt";

    private static final String currentBaseFile = "RPMComparator/16_02_2018/9.3-CENTOS-AZURE-PRE.txt";
    private static final String currentTargetFile = "RPMComparator/16_02_2018/9.3-CENTOS-AZURE-POST.txt";
    private static final String L7PFile = "RPMComparator/16_02_2018/L7P.txt";

    private static final String azureHost = "52.163.118.9";
    private static final String amiHost = "35.161.252.37";
    private static final String ovaHost = "10.130.5.161";
    private static final String username = "root";
    private static final String secret = "7layer";

    public static void main(String[] args) throws Exception{
        //Uncomment when populating L7P for RHEL for first time
        //getL7PRPMs(rhelPath);

        //Uncomment when populating L7P for RHEL for first time
        //getL7PRPMs(centosPath);

        //addL7PRPMSToFile();


        findDiff(currentBaseFile,currentTargetFile);
        //findDiff(currentTargetFile,L7PFile);
    }

    /**
     * Reading recursively the L7P directory and generating all the RPMs present in it and storing in an ArrayList
     */
    public static void getL7PRPMs(String path){

        File root = new File( path );
        File[] list = root.listFiles();

        if (list == null) return;

        for ( File f : list ) {
            if (f.isDirectory()) {
                getL7PRPMs(f.getAbsolutePath());
            } else {
                if (f.getAbsoluteFile().getAbsolutePath().indexOf(".rpm") != -1) {
                    rpmList.add(f.getAbsoluteFile().getName().substring(0,f.getAbsoluteFile().getName().indexOf(".rpm")));
                }
            }
        }
    }

    /**
     *  Runs a remote ssh command on a given host using JSCH library and stores the terminal output in a file locally
     */
    public static void runSSHCommand(String command, String file, String host)throws Exception{
        File writeTo = new File(file);
        FileWriter fw = null;
        BufferedWriter bw = null;
        try{
            fw = new FileWriter(writeTo);
            bw = new BufferedWriter(fw);
            try {

                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, host, 22);
                session.setPassword(secret);
                session.setConfig(config);
                session.connect();

                Channel channel = session.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);
                channel.setInputStream(null);
                InputStream in = channel.getInputStream();
                channel.connect();
                byte[] tmp = new byte[1024];
                while (true) {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, 1024);
                        if (i < 0) break;
                        bw.write(new String(tmp, 0, i));
                    }
                    if (channel.isClosed()) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ee) {
                    }
                }
                channel.disconnect();
                session.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }finally {
            bw.close();
        }
    }

    /**
     *  Stores the ArrayList containing the L7P rpms to a file for later retrieval
     */
    public static void addL7PRPMSToFile() throws Exception{
        //L7P RPM List File
        File writeTo = new File(L7PFile);
        FileWriter fw = null;
        BufferedWriter bw = null;
        try{
            fw = new FileWriter(writeTo);
            bw = new BufferedWriter(fw);
            Iterator<String> iter = rpmList.iterator();
            while(iter.hasNext()){
                bw.write(iter.next() + "\n");
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }finally{
            bw.close();
        }
    }

    /**
     * Reads a file containing RPM list and converts them to a Sorted ArrayList and returns the List to be used
     */
    public static List<String> convertFilesToList(String fileName) throws Exception{
        File readFrom = null;
        FileReader fr = null;
        BufferedReader br = null;
        List<String> fileNames = new ArrayList<String>();
        try{
            readFrom = new File(fileName);
            fr = new FileReader(readFrom);
            br = new BufferedReader(fr);
            String line = br.readLine();
            while(line != null){
                fileNames.add(line);
                line = br.readLine();
            }
        }finally {
            br.close();
        }
        Collections.sort(fileNames);
        return fileNames;
    }

    /**
     * Writes ArrayList items to file
     */
    public static void writeListToFile(List<String> list, String fileName) throws Exception{
        File writeTo = null;
        FileWriter fw = null;
        BufferedWriter bw = null;
        List<String> fileNames = new ArrayList<String>();
        try{
            fw = new FileWriter(fileName);
            bw = new BufferedWriter(fw);
            Iterator<String> iter = list.iterator();
            while(iter.hasNext()){
                String fileN = iter.next();
                bw.write(fileN + "\n");
            }
        }finally {
            bw.close();
        }
    }

    /**
     * Contains RPMs after L7P installation to compare with the base list to determine what changed.
     */
    public static void findDiff(String baseFile, String targetFile)throws Exception{
        List<String> baseFilesList = extractPackagesFromFile(baseFile);
        System.out.println("BASE PACKAGES COUNT =" + baseFilesList.size());
        int extraCount = 0;
        int matchCount = 0;
        int leftCount = 0;
        List<String> targetFilesList = convertFilesToList(targetFile);
        List<String> matchedFromBase = new ArrayList<String>();
        List<String> extraRPMs = new ArrayList<String>();
        Iterator<String> iter = targetFilesList.iterator();
        while(iter.hasNext()){
            String filename = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(filename);
            m.find();
            if(!baseFilesList.contains(filename.substring(0,m.start()))){
                extraRPMs.add(filename);
                extraCount++;
            }else{
                matchedFromBase.add(filename);
                matchCount++;
            }
        }

        iter = baseFilesList.iterator();
        List<String> packgsTouched = extractPackagesFromList(matchedFromBase);
        List<String> packgsExtra = extractPackagesFromList(extraRPMs);
        while(iter.hasNext()){
            String file = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(file);
            m.find();
            if(!packgsTouched.contains(file)){
                if(!packgsExtra.contains(file)) {
                    packagesLeftForUpdateCheck.add(file);
                    leftCount++;
                }
            }
        }
        System.out.println("MATCHED PACKAGES COUNT =" + matchCount);
        if(extraCount != 0){
            System.out.println("EXTRA RPMs INSTALLED -> TRUE");
        }else {
            System.out.println("EXTRA RPMS INSTALLED -> FALSE");
        }
        System.out.println("LEFT PACKAGES COUNT = " + leftCount);
        System.out.println("EXTRA PACKAGES COUNT =" + extraCount);
        iter = extraRPMs.iterator();
        while(iter.hasNext()){
            System.out.println(iter.next());
        }
        System.out.println("PACKAGES NEEDING UPDATES");
        populateFullRPMNamesFromPkgLeftForUpdateCheck();
        initBasePackageList();
        initPlusPackageList();
        iter = fullPackagesLeftForUpdateCheck.iterator();
        while(iter.hasNext()){
            String str = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(str);
            if(m.find() && str.indexOf(".el6") != -1) {
                compareVersions(str);
            }
        }
    }

    /**
     * Compare versions of base RPMs and that of l7tech centos plus and base packages
     */
    public static boolean compareVersions(String source) {
        Pattern p = Pattern.compile("-\\d{1}");
        Matcher m = p.matcher(source);
        m.find();
        String pkg = source.substring(0, m.start());
        Set<String> mySet = new TreeSet<>();
        List<String> rpmFromMirrorBase = getRPMsFromMirror(pkg, basePackageRpms);
        List<String> rpmFromMirrorPlus = getRPMsFromMirror(pkg, plusPackageRpms);
        mySet.addAll(rpmFromMirrorBase);
        mySet.addAll(rpmFromMirrorPlus);
        List<String> sortedList = new ArrayList<>();
        sortedList.addAll(mySet);
        Collections.sort(sortedList, Collections.reverseOrder());
        if(sortedList.size() > 0) {
            if (sortedList.get(0).equalsIgnoreCase(source) || sortedList.contains(source)) {
            } else {
                System.out.println("OUTDATED: " + source + " " + sortedList);
                return true;
            }
        }
        return false;
    }

    /**
     * Fetch mirror RPMS based on the RPM package name
     */
    public static List<String> getRPMsFromMirror(String pkg, List<String> rpmList){
        Iterator<String> iter = rpmList.iterator();
        List<String> returnList = new ArrayList<String>();
        while(iter.hasNext()){
            String val = iter.next();
            if(val.startsWith(pkg))
                returnList.add(val);
        }
        return returnList;
    }

    /**
        Extracts just the package name without the versioning from the file containing RPM list
     */
    public static List<String> extractPackagesFromFile(String file) throws Exception{
        List<String> baseFilesList = convertFilesToList(file);
        List<String> newNames = new ArrayList<String>();
        Iterator<String> iter = baseFilesList.iterator();
        while(iter.hasNext()){
            String name = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(name);
            m.find();
            newNames.add(name.substring(0,m.start()));
        }
        return newNames;
    }

    /**
     * Extracts just the package names without the versioning from the arraylist contains RPMs
     */
    public static List<String> extractPackagesFromList(List<String> list) throws Exception{
        List<String> newNames = new ArrayList<String>();
        Iterator<String> iter = list.iterator();
        while(iter.hasNext()){
            String name = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(name);
            m.find();
            newNames.add(name.substring(0,m.start()));
        }
        return newNames;
    }

    /**
     * Copies base package RPMs from l7tech server and writes to a file and also populates an arraylist with just the RPM names
     */
    public static void initBasePackageList()throws Exception{
        BufferedWriter bw = null;
        try {
            URL url = new URL(mirrorBaseURL);
            URLConnection con = url.openConnection();
            InputStream in = con.getInputStream();
            String encoding = con.getContentEncoding();
            encoding = encoding == null ? "UTF-8" : encoding;
            String body = IOUtils.toString(in, encoding);
            File basePackage = new File(mirrorBaseFile);
            FileWriter fw = new FileWriter(basePackage);
            bw = new BufferedWriter(fw);
            bw.write(body);
            String[] lines = body.split("\n");
            for(String s: lines){
                Pattern p = Pattern.compile("href=\".+.rpm\"");
                Matcher m = p.matcher(s);
                if(m.find()){
                    String filteredStr = s.substring(m.start(),m.end());
                    plusPackageRpms.add(filteredStr.substring(filteredStr.indexOf("\"") + 1, filteredStr.lastIndexOf(".rpm")));
                }
            }
            System.out.println("MIRROR PLUS COUNT = " + plusPackageRpms.size());
        }finally {
            bw.close();
        }
    }

    /**
     * Copies plus package RPMs from l7tech server and writes to a file and also populates an arraylist with just the RPM names
    */
    public static void initPlusPackageList() throws Exception{
        BufferedWriter bw = null;
        try {
            URL url = new URL(mirrorPlusURL);
            URLConnection con = url.openConnection();
            InputStream in = con.getInputStream();
            String encoding = con.getContentEncoding();
            encoding = encoding == null ? "UTF-8" : encoding;
            String body = IOUtils.toString(in, encoding);
            File basePackage = new File(mirrorPlusFile);
            FileWriter fw = new FileWriter(basePackage);
            bw = new BufferedWriter(fw);
            String[] lines = body.split("\n");
            bw.write(body);
            for(String s: lines){
                Pattern p = Pattern.compile("href=\".+.rpm\"");
                Matcher m = p.matcher(s);
                if(m.find()){
                    String filteredStr = s.substring(m.start(),m.end());
                    basePackageRpms.add(filteredStr.substring(filteredStr.indexOf("\"") + 1, filteredStr.lastIndexOf(".rpm")));
                }
            }
            System.out.println("MIRROR BASE COUNT = " + basePackageRpms.size());
        }finally {
            bw.close();
        }
    }
    public static void populateFullRPMNamesFromPkgLeftForUpdateCheck() throws Exception{
        List<String> baseRPMs = convertFilesToList(currentBaseFile);
        Iterator<String> iter = baseRPMs.iterator();
        while(iter.hasNext()){
            String file = iter.next();
            Pattern p = Pattern.compile("-\\d{1}");
            Matcher m = p.matcher(file);
            m.find();
            if(packagesLeftForUpdateCheck.contains(file.substring(0,m.start()))){
                fullPackagesLeftForUpdateCheck.add(file);
            }
        }
        System.out.println("FULL PKG LENGTH = " + fullPackagesLeftForUpdateCheck.size());
        System.out.println("Fetching latest mirror RPM list from " + mirrorBaseURL + " and " + mirrorPlusURL + " ...");
    }

    /**
     * Helper method to create and extract RPMs and populate the same in corresponding files. Should be run on need basis
     */
    public static void testCode() throws Exception{
        //Uncomment for RHEL L7P RPM generation
        //getL7PRPMs(rhelPath);
        getL7PRPMs(centosPath);
        addL7PRPMSToFile();
        runSSHCommand("rpm -qa",centosAMIFile,amiHost);

        //L7P file count
        List<String> L7PCount = convertFilesToList(L7PFile);
        writeListToFile(L7PCount,L7PFile);
        System.out.println(L7PCount.size());

        //AMI file count
        List<String> AMICount = convertFilesToList(centosAMIFile);
        writeListToFile(AMICount,centosAMIFile);
        System.out.println(AMICount.size());

        //Azure file count
        List<String> AzureCount = convertFilesToList(centosAzureFile);
        writeListToFile(AzureCount,centosAzureFile);
        System.out.println(AzureCount.size());

        //OVA file count
        List<String> OVACount = convertFilesToList(centosOVAFile);
        writeListToFile(OVACount,centosOVAFile);
        System.out.println(OVACount.size());
        System.out.println("DIFF1 AMI to AZURE**************************************************************");
        findDiff(centosAMIFile,centosAzureFile);
        System.out.println("DIFF2 AMI to OVA****************************************************************");
        findDiff(centosAMIFile,centosOVAFile);
        System.out.println("DIFF3 AZURE to AMI**************************************************************");
        findDiff(centosAzureFile,centosAMIFile);
        System.out.println("DIFF4 AZURE to OVA**************************************************************");
        findDiff(centosAzureFile,centosOVAFile);
    }
}

class PkgInformation{
    String formfactorRPM;
    String mirrorRPM;
    String formfactorVersion;
    String mirrorRPMVersion;
    boolean notUpdated;
}

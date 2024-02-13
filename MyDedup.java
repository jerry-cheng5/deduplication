import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MyDedup {

    public static byte[] containerBuffer = new byte[1048576];
    public static int containerBufferLen = 0;
    public static final int maxContainerSize = 1048576;

    public static int numFiles, prededupChunks, uniqueChunks, prededupBytes, uniqueBytes, numContainers, nextContainerID = 0;
    public static double dedupRatio = 1.0;

    public static Map<String, String> checkSums = new HashMap<String, String>(); 

    public static ArrayList<String> fileRecipe = new ArrayList<String>();
    
    public static boolean isPowerOfTwo(String arg) {
        int i = Integer.parseInt(arg);
        return (i > 0) && ((i & (i - 1)) == 0);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static boolean isAnchorPoint(long fp, long mask) {
        return (fp & mask) == 0 ? true : false;
    }

    public static String getChecksum(byte[] buffer, int chunkSize) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(buffer, 0, chunkSize);
            byte[] checksumBytes = md.digest();
            String checksum = bytesToHex(checksumBytes);
            return checksum;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public static void processChunk(byte[] buffer, int chunkSize) {
        String checkSum = getChecksum(buffer, chunkSize);

        if (!checkSums.containsKey(checkSum)) {
            if (containerBufferLen + chunkSize > maxContainerSize) { // If exceeds max container size
                String newContainerFilePath = "Container" + nextContainerID + ".txt";
                nextContainerID++;

                // Flush containerBuffer to new file
                try (FileOutputStream fos = new FileOutputStream(newContainerFilePath)) {
                    fos.write(containerBuffer, 0, containerBufferLen);
                    numContainers++;
                    containerBufferLen = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Add checksum to index set
            checkSums.put(checkSum, "Container" + nextContainerID + ".txt:" + containerBufferLen + ":" + chunkSize);

            // Copy chunk to containerBuffer and add address of chunk to file recipe
            System.arraycopy(buffer, 0, containerBuffer, containerBufferLen, chunkSize);
            containerBufferLen += chunkSize;

            // Update statistics
            uniqueChunks++;
            uniqueBytes += chunkSize;
        }

        fileRecipe.add(checkSum);
        prededupChunks++;
        prededupBytes += chunkSize;
        dedupRatio = (double) prededupBytes / uniqueBytes;
    }

    public static long computeRFP(int s, int d, int q, int m, byte[] buffer, long prevFP, int[] preComputed) {
        long FP = 0;
        if (s == 0) {
            int tempSum = 0;
            for (int i = 0; i < m; i++) {
                tempSum += (((buffer[i] % q) * ((int)Math.pow(d, m - 1 - i) % q)) % q);
                // tempSum += (buffer[i] * (int)Math.pow(d, m - 1 - i));
            }
            FP = tempSum % q;
        }
        else {
            // FP = ((d * (prevFP - preComputed[s])) + buffer[s + m - 1]) % q;
            FP = ((((d % q) * (((prevFP % q) - preComputed[s]) % q)) % q) + (buffer[s + m - 1] % q)) % q;
        }

        return FP;
    }

    public static void initializeVariables() {
        // read in MyDedup.index
        // extract all statistics + checksum values

        String filePath = "MyDedup.index";
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
                try (PrintWriter writer = new PrintWriter(file)) {
                    writer.println("0"); // # files
                    writer.println("0"); // # pre-dedup chunks
                    writer.println("0"); // # unique chunks
                    writer.println("0"); // # bytes of pre-dedup chunks
                    writer.println("0"); // # bytes of unique chunks
                    writer.println("0"); // # containers
                    writer.println("1"); // Dedup ratio
                    writer.println("0"); // next container id = 0
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            numFiles = Integer.parseInt(reader.readLine());
            prededupChunks = Integer.parseInt(reader.readLine());
            uniqueChunks = Integer.parseInt(reader.readLine());
            prededupBytes = Integer.parseInt(reader.readLine());
            uniqueBytes = Integer.parseInt(reader.readLine());
            numContainers = Integer.parseInt(reader.readLine());
            dedupRatio = Double.parseDouble(reader.readLine());
            nextContainerID = Integer.parseInt(reader.readLine());

            String line;
            while ((line = reader.readLine()) != null) {
                String[] temp = line.split(",");
                checkSums.put(temp[0], temp[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateVariables() {
        File file = new File("MyDedup.index");

        if (file.exists()) {
            file.delete();
        }

        try {
            file.createNewFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println(numFiles); // # files
                writer.println(prededupChunks); // # pre-dedup chunks
                writer.println(uniqueChunks); // # unique chunks
                writer.println(prededupBytes); // # bytes of pre-dedup chunks
                writer.println(uniqueBytes); // # bytes of unique chunks
                writer.println(numContainers); // # containers
                writer.println(dedupRatio); // Dedup ratio
                writer.println(nextContainerID); // next container id

                for (Map.Entry<String, String> kvp : checkSums.entrySet()) {
                    writer.println(kvp.getKey() + "," + kvp.getValue());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    public static void createFileRecipe(File f) {
        File file = new File("fileRecipes/" + f.getName());

        try {
            file.getParentFile().mkdirs();

            if (file.exists()) {
                file.delete();
            }

            file.createNewFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                for (String s : fileRecipe) {
                    writer.println(s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        return;
    }

    public static void printStatistics() {
        System.out.println("Report Output:");
        System.out.println("Total number of files that have been stored: " + numFiles);
        System.out.println("Total number of pre-deduplicated chunks in storage: " + prededupChunks);
        System.out.println("Total number of unique chunks in storage: " + uniqueChunks);
        System.out.println("Total number of bytes of pre-deduplicated chunks in storage: " + prededupBytes);
        System.out.println("Total number of bytes of unique chunks in storage: " + uniqueBytes);
        System.out.println("Total number of containers in storage: " + numContainers);
        System.out.println(String.format("Deduplication ratio: %.2f", dedupRatio));
    }

    public static void upload(int minChunk, int avgChunk, int maxChunk, int d, String filePathName) {
        try {
            initializeVariables();

            File file = new File(filePathName);
            FileInputStream inputStream = new FileInputStream(file);

            long mask = avgChunk - 1;
            int m = minChunk;
            int q = avgChunk;

            byte[] buffer = new byte[maxChunk];
            int bytesReadIn = 0;
            int currentBufferSize = 0;
            long currentFingerprint = 0;

            while ((bytesReadIn = inputStream.read(buffer, currentBufferSize, maxChunk - currentBufferSize)) != -1) {
                currentBufferSize += bytesReadIn;

                if (currentBufferSize < m) {
                    processChunk(buffer, currentBufferSize);
                    currentBufferSize = 0;
                    break;
                }

                int[] preComputed = new int[currentBufferSize];
                for (int i = 0; i < currentBufferSize; i++) {
                    preComputed[i] = (int) ((((int)Math.pow(d, m - 1) % q) * (buffer[i] % q)) % q);
                }

                for (int i = 0; i <= currentBufferSize - m; i++) {
                    // compute fingerprint
                    currentFingerprint = computeRFP(i, d, q, m, buffer, currentFingerprint, preComputed);

                    if (isAnchorPoint(currentFingerprint, mask) || i == currentBufferSize - m) {
                        processChunk(buffer, i + m);
                        System.arraycopy(buffer, i + m, buffer, 0, currentBufferSize - (i + m));
                        currentBufferSize -= (i + m);

                        break;
                    }
                }
            }

            // Process remaining chunk if there is
            if (currentBufferSize > 0) {
                processChunk(buffer, currentBufferSize);
            }

            // Flush tail container buffer
            if (containerBufferLen > 0) {
                String newContainerFilePath = "Container" + nextContainerID + ".txt";
                nextContainerID++;

                // Flush containerBuffer to new file
                try (FileOutputStream fos = new FileOutputStream(newContainerFilePath)) {
                    fos.write(containerBuffer, 0, containerBufferLen);
                    numContainers++;
                    containerBufferLen = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            numFiles++;

            // Update statistics and checksums in MyDedup.index
            updateVariables();
            createFileRecipe(file);
            printStatistics();

            inputStream.close();
        } catch (FileNotFoundException e) {
            System.err.println("File not found " + e);
        } catch (IOException e) {
            System.err.println("Unable to read file " + e);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void download(String file, String dest) {
        File tempFile = new File(file);
        File fileRecipe = new File("fileRecipes/" + tempFile.getName());
        File destFile = new File(dest);

        if (!fileRecipe.exists()) {
            System.err.println("Your file does not exist in the cloud.");
            return;
        }
        if (destFile.exists()) {
            destFile.delete();
        }
        try {
            destFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        initializeVariables();

        try (BufferedReader br = new BufferedReader(new FileReader(fileRecipe));
             FileOutputStream fileOutputStream = new FileOutputStream(destFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] address = checkSums.get(line).split(":");
                String containerFileName = address[0];
                int offset = Integer.parseInt(address[1]);
                int length = Integer.parseInt(address[2]);
                byte[] chunk = new byte[length];

                File container = new File(containerFileName);

                // System.out.println("Reading from: " + checkSums.get(line));
                
                try (FileInputStream inputStream = new FileInputStream(container)) {
                    inputStream.skip(offset);
                    int bytesRead = inputStream.read(chunk);

                    if (bytesRead != length) {
                        throw new IOException("Unexpected end of file");
                    }

                    fileOutputStream.write(chunk);
                } catch (Exception e) {
                    throw e;
                }
            }

            // System.out.println("File processing completed.");

        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Error: Not enough arguments:\nupload [minChunk] [avgChunk] [maxChunk] [base] [filepath]\ndownload [file to download] [local file name]");
            return;
        }

        String functionality = args[0];

        if (functionality.equals("upload")) {
            if (args.length != 6 || !isPowerOfTwo(args[1]) || !isPowerOfTwo(args[2]) || !isPowerOfTwo(args[3])) {
                System.err.println("Error: Need 6 arguments, chunk sizes need to be powers of two");
                return;
            }
            upload(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), args[5]);
        }
        else if (functionality.equals("download")) {
            download(args[1], args[2]);
        }
        else {
            System.err.println("Unknown function: " + functionality);
        }
    }
}

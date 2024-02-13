# Deduplication Program
This is a simple upload/download program written in java, with inline deduplication features to minimize data stored.

## How to run
1. Clone the repo.
2. Navigate to the repo folder in terminal and type `make`.
3. To upload a file, type `java MyDedup upload [minChunk] [avgChunk] [maxChunk] [base] [filepath]`. (The chunk sizes must be powers of 2.) This will upload your file and print out some deduplication statistics. 
4. To download a file, type `java MyDedup download [file to download] [new local file name]`. This will 'download' the file as the file name specified.

There are a couple test files already the `input` folder.

### Example:
```
make
java MyDedup upload 4 8 32 2 ./input/KJV_Bible.txt
java MyDedup upload 2 16 32 2 ./input/shakespeare.txt
java MyDedup download KJV_Bible.txt local_file.txt
```

You can run `diff` to confirm that the original and downloaded files are the same.

## How the code works

### 1. Fingerprinting
This deduplication implementation uses variable-sized hashing. To get the size of the chunk, the program reads the file and uses Rabin-fingerprinting (RFP) to get anchor points. Once the predetermined 'critical' RFP value is met, this is the edge of the chunk. This is done iteratively for the whole file.

### 2. Chunking
Once we have split the file into chunks, the program will generate fingerprints for each chunk with SHA-1. The fingerprint is the compared to existing fingerprints, and we only store the data if the fingerprint is unique.

### 3. Indexing
A fingerprint index file (`MyDedup.index`) is generated to keep track of all the chunk fingerprints, and the location of the data it represents. 

A file recipe is also generated for the uploaded file, storing the fingerprints that represent the chunks for the file. This file recipe is stored in `fileRecipes`.

The actual chunk data is stored in containers named `ContainerX.txt`.

On download, the program goes through the file recipe and uses the index file to retrieve the chunk data.
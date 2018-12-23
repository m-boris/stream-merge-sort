package com.company;

import com.company.data.LineData;
import com.company.data.StreamSortStatisticData;
import com.company.worker.StreamProducer;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StreamSorterImpl implements StreamSorter {

    @Override
    public int sortedChunkMerger(String processingFolder, long numOfChunks, String output) throws IOException {
        FileWriter writer = null;
        List<StreamProducer> inputs = null;
        int numberOfWroteLines = 0;
        try {
            writer = new FileWriter(output);
            inputs = intStreamProducers(processingFolder, numOfChunks);
            LineData lineData;
            do {
                lineData = null;
                Optional<StreamProducer> o = inputs.stream().filter(sp -> sp.getCurrentLineData() != null).
                        min((sp1, sp2) ->
                                StringUtils.compare(sp1.getCurrentLineData().getValue(), sp2.getCurrentLineData().getValue()));
                if (o.isPresent()) {
                    lineData = o.get().popLineData();
                    writeLineData(writer, lineData);
                    numberOfWroteLines++;
                }
            } while (lineData != null);
        } finally {
            if (inputs != null) {
                inputs.forEach(i -> i.shutdown());
            }
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        }
        return numberOfWroteLines;
    }

    private List<StreamProducer> intStreamProducers(String processingFolder, long numOfChunks) throws IOException {
        List<StreamProducer> inputs = new ArrayList<>();
        for (int i = 0; i < numOfChunks; i++) {
            String fileName = getProcessingFileName(processingFolder, i);
            StreamProducer sp = new StreamProducer(fileName);
            sp.init();
            inputs.add(sp);
        }
        return inputs;
    }


    @Override
    public StreamSortStatisticData sortedChunkSplitter(String inputFileName, String processingFolder, int chunkSize) throws IOException {
        int chunkNumber = 0;
        int numberOfLines = 0;
        List<LineData> list;
        try (BufferedReader fileInputStream = new BufferedReader(new InputStreamReader(new FileInputStream(inputFileName)))) {
            do {
                list = readChunk(fileInputStream, chunkSize);
                if (list != null && !list.isEmpty()) {
                    numberOfLines += list.size();
                    sortList(list);
                    saveChunk(list, getProcessingFileName(processingFolder, chunkNumber));
                    chunkNumber++;
                }
            } while (list != null && !list.isEmpty());
        }
        return new StreamSortStatisticData(chunkNumber, numberOfLines);
    }

    private String getProcessingFileName(String processingFolder, int chunkNumber) {
        return processingFolder + "/p" + chunkNumber + ".csv";
    }

    private void saveChunk(List<LineData> list, String outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            for (LineData lineData : list) {
                writeLineData(writer, lineData);
            }
        }
    }

    private void writeLineData(FileWriter writer, LineData lineData) throws IOException {
        writer.write(lineData.getLineAsString());
        writer.append(System.lineSeparator());
    }


    private void sortList(List<LineData> list) {
        list.sort((line1, line2) -> StringUtils.compare(line1.getValue(), line2.getValue()));

    }

    private List<LineData> readChunk(BufferedReader fileInputStream, long chunkSize) throws IOException {
        String line;
        List<LineData> lineData = new ArrayList<>();
        while (chunkSize > 0 && (line = fileInputStream.readLine()) != null) {
            if (LineData.isLineData(line)) {
                lineData.add(new LineData(line));
                chunkSize--;
            }
        }
        return lineData;
    }

}

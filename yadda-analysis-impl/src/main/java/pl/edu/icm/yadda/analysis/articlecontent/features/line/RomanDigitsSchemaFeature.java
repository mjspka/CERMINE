package pl.edu.icm.yadda.analysis.articlecontent.features.line;

import pl.edu.icm.yadda.analysis.classification.features.FeatureCalculator;
import pl.edu.icm.yadda.analysis.textr.model.BxLine;
import pl.edu.icm.yadda.analysis.textr.model.BxPage;

/**
 *
 * @author Dominika Tkaczyk (dtkaczyk@icm.edu.pl)
 */
public class RomanDigitsSchemaFeature implements FeatureCalculator<BxLine, BxPage> {

    private static String featureName = "RomanDigitsSchema";

    @Override
    public String getFeatureName() {
        return featureName;
    }

    @Override
    public double calculateFeatureValue(BxLine line, BxPage page) {
        return (line.toText().matches("^[IVX]+\\.? [A-Z].*$")) ? 1 : 0;
    }
    
}
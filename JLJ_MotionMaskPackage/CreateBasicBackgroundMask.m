function [bF] = CreateBasicBackgroundMask(gF,ForegroundThresh,BusyThresh)
% This function creates a basic foreground mask by assuming that over a
% long time history (i.e. 10 second clip or more) a particular pixel on a
% stationary camera will be background more often than foreground.
% Therefore, the median value should be a background value.  Pixel values
% are transformed to their absolute distance from the median value and then
% thresholded.  Pixel values in time that were more than ForegroundThresh
% away from the median are considered foreground, those that are not are
% considered background.  Then we look at how many pixels in an individual
% pixel's time history were labeled foreground.  If more than a BusyThresh
% were labeled foreground, then the pixel is labeled foreground for the
% entire time period.  The idea there is that if there was a lot of changes
% going on, this mask doesn't try to get it correct the first time and
% labels it all foreground so that another method can fix it later.
%--------------------------------------------------------------------------
% INPUT VARIABLES
% gF = gray frame stack
% ForegroundThresh = dist from median pixel value over time history to be
%                    considered foreground (not background)
% BusyThresh = We dont expect moving objects to make up a large chunk of
%              the time history of a particular pixel.  So if this 
%              percentage of pixels across the entire history or more are 
%              considered foreground, we are going to just call the entire
%              thing foreground and deal with it later
% -------------------------------------------------------------------------
    gF = double(gF);
    sizeGF = size(gF);
    tic
    bF = false(sizeGF);
    Indices = [1:sizeGF(3)];
    for i=1:length(gF(:,1,1))
        for j=1:length(gF(1,:,1))
            PixelHistory = gF(i,j,:); % Extract time history of pixel(i,j)
            Median = median(PixelHistory);
            DistFromMedian = abs(PixelHistory -  Median);
            ForegroundMask = DistFromMedian>=ForegroundThresh;
            if length(Indices(ForegroundMask)) >= BusyThresh*sizeGF(3)
                % If more than BusyThresh% of line is foreground, classify entire line as
                % foreground instead.  Idea being more data is better than
                % possible deleting important info.  We can use other methods
                % to correctly classify these areas
                bF(i,j,:) = true;
            else
                bF(i,j,Indices(ForegroundMask)) = true;
            end
        end
    end
    InitialBackgroundMaskTime = toc
end


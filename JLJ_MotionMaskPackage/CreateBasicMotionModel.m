function [bF] = CreateBasicMotionModel(gF, MotionThresh)
% This function uses a 3D sobel edge detector in the time (z) direction of
% the gray stack.  After the kernel is applied and the absolute value is
% taken of the result, the stack has values corresponding to changes in
% time for each pixel value.  This result is scaled to the max change and
% then the result is thresholded as a percentage of the maximum change
% throughout the entire time sequence.
% The 3 layers of a Sobel Kernel: |1 2 1|  |0 0 0|   |-1 -2 -1|
%                                 |2 4 2|  |0 0 0|   |-2 -4 -2|
%                                 |1 2 1|  |0 0 0|   |-1 -2 -1|
% One layer sum = 16.  The threshold is a pixel distance specified assuming
% the input image range is uint8([0-255]).  So if the threshold is a change
% of 20, this results in a threshold of 20*16 = 320 after the sobel kernel
% is applied.  The threshold is created under the assumption that a 3x3 
% patch of pixels went from 0 to 20 inside the kernel.  The kernel result
% for this would be 320 - 0 = 320.
    % -----------------------------------------------
    %------------------------------------------------------
    % Identify Discontinuities ----
    tic
    bF = imfilter(double(gF),fspecial3('sobel','Z'),'symmetric');
    bF = double((abs(bF)));
    bF = bF > MotionThresh*16; % Apply thresh
    bF = uint8(255*bF);
    InitialMotionMaskTime = toc
end


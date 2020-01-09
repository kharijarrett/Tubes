function [gF] = ConvertVidToGray(InputVideoName,fSkip,Start, Duration)
% This function reads in a video while skipping every "fSkip" frames.  The
% saved video is in grayscale.  There is also a specified limit to how many
% frames should be saved as well as what frame to start saving.
% if LengthLimit == 0, the entire video is read
%-------------------------------------------------------------------------
fSkip = fSkip + 1; % just to make math work with mod function
Vid = VideoReader(InputVideoName); % video handle
% This section saves all the frames into a cell array
i=1;
j=1;
fprintf('Loading Video ...\n');
while hasFrame(Vid)
    frame = readFrame(Vid);
    if j>=Start
        if mod(j,fSkip)==0
            gF(:,:,i) = rgb2gray(frame);
            if Duration ~= 0 && i == Duration
                break;
            end
            i=i+1;
        end
    end
    j=j+1;
end
fprintf('Loaded Video\n');
end


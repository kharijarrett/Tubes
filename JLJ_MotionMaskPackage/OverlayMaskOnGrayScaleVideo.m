function [] = OverlayMaskOnGrayScaleVideo(gF,bF,MaskVideoName)
% This function overlays a binary mask as green pixels on a grayscale video
% The function expects both the grayscale and binary mask videos as 3D
% matrices as well as the new video's name.
NewVid = VideoWriter(MaskVideoName,'MPEG-4'); % video handle
NewVid.FrameRate = 30;
open(NewVid);
for i=1:length(gF(1,1,:))
    frame(:,:,1) = gF(:,:,i);
    frame(:,:,2) = uint8(gF(:,:,i) + bF(:,:,i));
    frame(:,:,3) = gF(:,:,i);
    writeVideo(NewVid, frame)
end
close(NewVid)
end

